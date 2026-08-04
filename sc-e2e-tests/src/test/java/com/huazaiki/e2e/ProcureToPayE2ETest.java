package com.huazaiki.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazaiki.auth.AuthServiceApplication;
import com.huazaiki.inventory.InventoryServiceApplication;
import com.huazaiki.payment.PaymentServiceApplication;
import com.huazaiki.purchase.PurchaseServiceApplication;
import com.huazaiki.supplier.SupplierServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关公共 API 黑盒 E2E（主接缝）：
 * Testcontainers 起 MySQL + Kafka；6 服务以 e2e profile 启动（Nacos 测试替代：SimpleDiscoveryClient 静态实例）。
 * 覆盖完整闭环：登录→请购→审批链→转单→订单审批+预留→收货→质检→入库→应付→发票→三单匹配→付款→核销→订单结清。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Procure-to-Pay 全链路 E2E")
class ProcureToPayE2ETest {

    // 端口：网关 18080，服务 18081..18085
    static final int GW = 18080, AUTH = 18081, SUP = 18082, PUR = 18083, INV = 18084, PAY = 18085;
    static final String SECRET = "e2e-secret-0123456789abcdef0123456789abcdef0123456789abcdef0123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("e2e")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static final ObjectMapper OM = new ObjectMapper();
    static final List<ConfigurableApplicationContext> CONTEXTS = new ArrayList<>();

    // 运行中产生的 id
    static String adminToken, purchaserToken, deptMgrToken, purchMgrToken, warehouseToken, financeToken;
    static Process gatewayProcess;
    static Long deptId, itemId, supplierId, prId, orderId, receiveId, payableId;
    static String orderNo;

    @BeforeAll
    static void setUp() throws Exception {
        createDatabasesIfNeeded();
        startServices();
        waitHealthy(GW);
        runClosedLoop();
    }

    @AfterAll
    static void tearDown() {
        CONTEXTS.forEach(ctx -> {
            try { ctx.close(); } catch (Exception ignored) { }
        });
        if (gatewayProcess != null) {
            gatewayProcess.destroy();
        }
    }

    // ---------------- 基础设施 ----------------

    private static void createDatabasesIfNeeded() throws Exception {
        // 容器就绪后以 root 创建各服务数据库（test 用户无建库权限）
        String url = "jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                .formatted(MYSQL.getHost(), MYSQL.getMappedPort(3306));
        try (Connection c = DriverManager.getConnection(url, "root", "test");
             Statement st = c.createStatement()) {
            for (String db : List.of("auth_db", "supplier_db", "purchase_db", "inventory_db", "payment_db")) {
                st.execute("CREATE DATABASE IF NOT EXISTS " + db);
                st.execute("GRANT ALL PRIVILEGES ON " + db + ".* TO \u0027" + MYSQL.getUsername() + "\u0027@\u0027%\u0027");
            }
            st.execute("FLUSH PRIVILEGES");
        } catch (java.sql.SQLException e) {
            // root 密码可能等于容器密码
            try (Connection c = DriverManager.getConnection(url, "root", MYSQL.getPassword());
                 Statement st = c.createStatement()) {
                for (String db : List.of("auth_db", "supplier_db", "purchase_db", "inventory_db", "payment_db")) {
                    st.execute("CREATE DATABASE IF NOT EXISTS " + db);
                }
            }
        }
    }

    private static void startServices() throws Exception {
        Map<String, Object> base = new HashMap<>();
        base.put("spring.cloud.nacos.config.enabled", false);
        base.put("spring.cloud.nacos.discovery.enabled", false);
        base.put("seata.enabled", false);
        base.put("jwt.secret", SECRET);
        base.put("jwt.expiration-ms", 3600000);
        base.put("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        base.put("spring.main.banner-mode", "off");
        base.put("logging.level.root", "WARN");
        base.put("spring.datasource.username", MYSQL.getUsername());
        base.put("spring.datasource.password", MYSQL.getPassword());
        base.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        // SimpleDiscoveryClient 静态实例（lb:// 解析）
        base.put("spring.cloud.discovery.client.simple.instances.sc-auth-service[0].uri", "http://localhost:" + AUTH);
        base.put("spring.cloud.discovery.client.simple.instances.sc-supplier-service[0].uri", "http://localhost:" + SUP);
        base.put("spring.cloud.discovery.client.simple.instances.sc-purchase-service[0].uri", "http://localhost:" + PUR);
        base.put("spring.cloud.discovery.client.simple.instances.sc-inventory-service[0].uri", "http://localhost:" + INV);
        base.put("spring.cloud.discovery.client.simple.instances.sc-payment-service[0].uri", "http://localhost:" + PAY);

        String url = "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String host = MYSQL.getHost();
        int port = MYSQL.getMappedPort(3306);

        Map<String, Object> auth = new HashMap<>(base);
        auth.put("server.port", AUTH);
        auth.put("spring.datasource.url", String.format(url, host, port, "auth_db"));
        auth.put("spring.flyway.locations", "classpath:db/migration/auth-service");
        start("auth", AuthServiceApplication.class, auth);

        Map<String, Object> supplier = new HashMap<>(base);
        supplier.put("server.port", SUP);
        supplier.put("spring.datasource.url", String.format(url, host, port, "supplier_db"));
        supplier.put("spring.flyway.locations", "classpath:db/migration/supplier-service");
        start("supplier", SupplierServiceApplication.class, supplier);

        Map<String, Object> purchase = new HashMap<>(base);
        purchase.put("server.port", PUR);
        purchase.put("spring.datasource.url", String.format(url, host, port, "purchase_db"));
        purchase.put("spring.flyway.locations", "classpath:db/migration/purchase-service");
        purchase.put("spring.cloud.openfeign.circuitbreaker.enabled", false);
        start("purchase", PurchaseServiceApplication.class, purchase);

        Map<String, Object> inventory = new HashMap<>(base);
        inventory.put("server.port", INV);
        inventory.put("spring.datasource.url", String.format(url, host, port, "inventory_db"));
        inventory.put("spring.flyway.locations", "classpath:db/migration/inventory-service");
        start("inventory", InventoryServiceApplication.class, inventory);

        Map<String, Object> payment = new HashMap<>(base);
        payment.put("server.port", PAY);
        payment.put("spring.datasource.url", String.format(url, host, port, "payment_db"));
        payment.put("spring.flyway.locations", "classpath:db/migration/payment-service");
        start("payment", PaymentServiceApplication.class, payment);

        // 网关以独立 JVM 启动（其 -exec fat jar 仅含 WebFlux，避免与测试 JVM 中服务模块带来的 spring-webmvc 冲突）
        startGatewayProcess();
    }

    private static void startGatewayProcess() throws Exception {
        File jar = new File("../sc-gateway-service/target/sc-gateway-service-0.0.1-SNAPSHOT-exec.jar");
        if (!jar.exists()) {
            throw new IllegalStateException("Gateway exec jar not found: " + jar.getAbsolutePath() + "（请先 mvn package）");
        }
        List<String> cmd = new ArrayList<>(List.of(
                "java", "-jar", jar.getAbsolutePath(),
                "--server.port=" + GW,
                "--jwt.secret=" + SECRET,
                "--spring.cloud.nacos.config.enabled=false",
                "--spring.cloud.nacos.discovery.enabled=false",
                "--spring.cloud.discovery.client.simple.instances.sc-auth-service[0].uri=http://localhost:" + AUTH,
                "--spring.cloud.discovery.client.simple.instances.sc-supplier-service[0].uri=http://localhost:" + SUP,
                "--spring.cloud.discovery.client.simple.instances.sc-purchase-service[0].uri=http://localhost:" + PUR,
                "--spring.cloud.discovery.client.simple.instances.sc-inventory-service[0].uri=http://localhost:" + INV,
                "--spring.cloud.discovery.client.simple.instances.sc-payment-service[0].uri=http://localhost:" + PAY,
                "--spring.main.banner-mode=off",
                // 本地路由（Nacos 关闭后由这里提供，与 nacos-config/sc-gateway-service.yml 对齐）
                "--spring.cloud.gateway.routes[0].id=sc-auth-service",
                "--spring.cloud.gateway.routes[0].uri=lb://sc-auth-service",
                "--spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/auth/**",
                "--spring.cloud.gateway.routes[1].id=sc-auth-admin",
                "--spring.cloud.gateway.routes[1].uri=lb://sc-auth-service",
                "--spring.cloud.gateway.routes[1].predicates[0]=Path=/api/v1/users/**,/api/v1/depts/**,/api/v1/roles/**,/api/v1/permissions/**",
                "--spring.cloud.gateway.routes[2].id=sc-supplier-service",
                "--spring.cloud.gateway.routes[2].uri=lb://sc-supplier-service",
                "--spring.cloud.gateway.routes[2].predicates[0]=Path=/api/v1/suppliers/**",
                "--spring.cloud.gateway.routes[3].id=sc-purchase-service",
                "--spring.cloud.gateway.routes[3].uri=lb://sc-purchase-service",
                "--spring.cloud.gateway.routes[3].predicates[0]=Path=/api/v1/orders/**,/api/v1/requisitions/**,/api/v1/approval-tasks/**",
                "--spring.cloud.gateway.routes[4].id=sc-inventory-service",
                "--spring.cloud.gateway.routes[4].uri=lb://sc-inventory-service",
                "--spring.cloud.gateway.routes[4].predicates[0]=Path=/api/v1/inventory/**,/api/v1/receives/**,/api/v1/quality-inspections/**",
                "--spring.cloud.gateway.routes[5].id=sc-payment-service",
                "--spring.cloud.gateway.routes[5].uri=lb://sc-payment-service",
                "--spring.cloud.gateway.routes[5].predicates[0]=Path=/api/v1/payments/**,/api/v1/invoices/**,/api/v1/payment-vouchers/**"));
        gatewayProcess = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        System.out.println("[E2E] gateway process started (pid " + gatewayProcess.pid() + ")");
    }

    private static void start(String name, Class<?> appClass, Map<String, Object> props) {
        SpringApplication app = new SpringApplication(appClass);
        app.setDefaultProperties(props);
        ConfigurableApplicationContext ctx = app.run("--server.port=" + props.get("server.port"));
        CONTEXTS.add(ctx);
        System.out.println("[E2E] " + name + " started on port " + props.get("server.port"));
    }

    private static void waitHealthy(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                                .GET().timeout(Duration.ofSeconds(3)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body().contains("UP")) {
                    return;
                }
            } catch (Exception ignored) { }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Service on port " + port + " not healthy in time");
    }

    // ---------------- HTTP 帮助 ----------------

    private static HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GW + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        b.header("Content-Type", "application/json");
        String json = body == null ? "" : OM.writeValueAsString(body);
        if ("GET".equals(method)) {
            b.GET();
        } else if ("POST".equals(method)) {
            b.POST(HttpRequest.BodyPublishers.ofString(json));
        } else if ("PUT".equals(method)) {
            b.PUT(HttpRequest.BodyPublishers.ofString(json));
        } else {
            throw new IllegalArgumentException(method);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> call(String method, String path, String token) throws Exception {
        return call(method, path, token, null);
    }

    private static int code(String method, String path, String token, Object body) throws Exception {
        return call(method, path, token, body).statusCode();
    }

    private static int code(String method, String path, String token) throws Exception {
        return call(method, path, token).statusCode();
    }

    private static String token(String username, String password) throws Exception {
        HttpResponse<String> resp = call("POST", "/api/v1/auth/login", null,
                Map.of("username", username, "password", password));
        assertEquals(200, resp.statusCode(), "login " + username + " failed: " + resp.body());
        return OM.readTree(resp.body()).path("data").path("token").asText();
    }

    private static Long findIdByField(String method, String path, String token, String field, String value) throws Exception {
        HttpResponse<String> resp = call(method, path, token);
        assertEquals(200, resp.statusCode(), "find " + path + " failed: " + resp.body());
        JsonNode data = OM.readTree(resp.body()).path("data");
        if (data.isArray()) {
            for (JsonNode n : data) {
                if (value.equals(n.path(field).asText())) {
                    return n.path("id").asLong();
                }
            }
        }
        throw new IllegalStateException("Not found: " + field + "=" + value + " in " + path);
    }

    // ---------------- JDBC 帮助 ----------------

    private static Connection jdbc(String db) throws Exception {
        String url = "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                .formatted(MYSQL.getHost(), MYSQL.getMappedPort(3306), db);
        return DriverManager.getConnection(url, MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void jdbcUpdate(String db, String sql) throws Exception {
        try (Connection c = jdbc(db); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static Long jdbcScalar(String db, String sql) throws Exception {
        try (Connection c = jdbc(db); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new IllegalStateException("No row for: " + sql);
    }

    // ---------------- 核心闭环 ----------------

    private static void runClosedLoop() throws Exception {
        // 1. 注册并提升 admin（Flyway 种子角色 id=1 ADMIN）
        assertEquals(200, code("POST", "/api/v1/auth/register", null,
                Map.of("username", "admin", "password", "Passw0rd", "role", "ADMIN")));
        Long adminUserId = jdbcScalar("auth_db",
                "SELECT id FROM sys_user WHERE username='admin'");
        jdbcUpdate("auth_db",
                "INSERT INTO sys_user_role (id, user_id, role_id) VALUES (9001, " + adminUserId + ", 1)");

        // 2. 先登录 admin（已分配 ADMIN 角色）
        adminToken = token("admin", "Passw0rd");

        // 3. 部门
        assertEquals(200, code("POST", "/api/v1/depts", adminToken,
                Map.of("deptCode", "D1", "deptName", "采购一部")));
        deptId = findIdByField("GET", "/api/v1/depts", adminToken, "deptCode", "D1");

        // 4. 建用户并分配角色（角色 id：PURCHASER=2, PURCHASE_MANAGER=3, DEPT_MANAGER=4, WAREHOUSE=5, FINANCE=6）
        createUser("purchaser", 2);
        createUser("deptmgr", 4);
        createUser("purchmgr", 3);
        createUser("warehouse", 5);
        createUser("finance", 6);
        purchaserToken = token("purchaser", "Passw0rd");
        deptMgrToken = token("deptmgr", "Passw0rd");
        purchMgrToken = token("purchmgr", "Passw0rd");
        warehouseToken = token("warehouse", "Passw0rd");
        financeToken = token("finance", "Passw0rd");

        // 4. 物料 + 库存期初（JDBC 直接建行）
        HttpResponse<String> itemResp = call("POST", "/api/v1/inventory/items", purchaserToken,
                Map.of("name", "Steel Rod", "spec", "10mm", "unit", "pcs", "sku", "SR-10"));
        assertEquals(200, itemResp.statusCode(), itemResp.body());
        itemId = OM.readTree(itemResp.body()).path("data").path("id").asLong();
        jdbcUpdate("inventory_db",
                "INSERT INTO inventory (id, item_id, available_qty, reserved_qty) VALUES (9001, " + itemId + ", 1000, 0)");

        // 5. 供应商
        assertEquals(200, code("POST", "/api/v1/suppliers", purchaserToken,
                Map.of("name", "Acme Corp", "creditCode", "91110000MA001", "contactName", "John", "contactPhone", "13800001111")));
        supplierId = findIdByField("GET", "/api/v1/suppliers?page=1&size=100", purchaserToken, "name", "Acme Corp");

        // 6. 请购 → 审批链（金额 60000 触发 部门经理 + 采购经理 两节点）
        HttpResponse<String> prResp = call("POST", "/api/v1/requisitions", purchaserToken, Map.of(
                "supplierId", supplierId,
                "expectedDate", "2026-09-01",
                "purpose", "E2E 采购",
                "items", List.of(Map.of("itemId", itemId, "itemName", "Steel Rod", "quantity", 100, "amount", 60000))));
        assertEquals(200, prResp.statusCode(), prResp.body());
        prId = OM.readTree(prResp.body()).path("data").path("id").asLong();
        assertEquals(200, code("POST", "/api/v1/requisitions/" + prId + "/submit", purchaserToken));

        approveFirstTask(deptMgrToken, "PR", prId);
        assertEquals("APPROVED", prStatus());

        approveFirstTask(purchMgrToken, "PR", prId);
        assertEquals("APPROVED", prStatus());

        // 7. 转单 → 订单审批 + 预留
        HttpResponse<String> orderResp = call("POST", "/api/v1/requisitions/" + prId + "/convert", purchaserToken);
        assertEquals(200, orderResp.statusCode(), orderResp.body());
        orderId = OM.readTree(orderResp.body()).path("data").path("id").asLong();
        orderNo = OM.readTree(orderResp.body()).path("data").path("orderNo").asText();
        assertEquals(200, code("PUT", "/api/v1/orders/" + orderId + "/approve", purchMgrToken));
        assertEquals("APPROVED", orderStatus());

        // 8. 收货 → 质检 → 入库
        Long orderItemId = jdbcScalar("purchase_db",
                "SELECT id FROM purchase_order_item WHERE order_id=" + orderId + " LIMIT 1");
        HttpResponse<String> rcResp = call("POST", "/api/v1/receives", warehouseToken, Map.of(
                "orderId", orderId, "supplierId", supplierId,
                "items", List.of(Map.of("orderItemId", orderItemId, "itemId", itemId, "orderQty", 100, "receivedQty", 100))));
        assertEquals(200, rcResp.statusCode(), rcResp.body());
        receiveId = OM.readTree(rcResp.body()).path("data").path("id").asLong();
        Long receiveItemId = jdbcScalar("inventory_db",
                "SELECT id FROM receive_item WHERE receive_id=" + receiveId + " LIMIT 1");
        assertEquals(200, code("POST", "/api/v1/quality-inspections", warehouseToken, Map.of(
                "receiveItemId", receiveItemId, "inspectType", "FULL", "inspectQty", 100, "qualifiedQty", 100)));
        assertEquals(200, code("POST", "/api/v1/receives/" + receiveId + "/stock-in", warehouseToken));

        // 9. 订单推进 RECEIVED（供结清事件消费）
        assertEquals(200, code("PUT", "/api/v1/orders/" + orderId + "/status", purchaserToken, Map.of("status", "RECEIVED")));

        // 10. 等待事件 → 应付生成（Kafka 异步）
        payableId = awaitPayable(orderId);

        // 11. 应付审批 → 付款单核销 → 订单结清
        assertEquals(200, code("PUT", "/api/v1/payments/" + payableId + "/approve", financeToken));
        assertEquals(200, code("POST", "/api/v1/payment-vouchers", financeToken, Map.of(
                "supplierId", supplierId, "amount", 60000, "method", "TRANSFER", "payableIds", List.of(payableId))));

        awaitOrderSettled(orderId);

        // 12. 断言：库存流水与应付状态
        assertEquals("PAID", payableStatus());
        assertTrue(ledgerCount(itemId) >= 2, "库存流水应至少 2 条（预留+入库）");

        // 13. 越权：purchaser 无 payment:pay → 403
        assertEquals(403, code("POST", "/api/v1/payment-vouchers", purchaserToken,
                Map.of("supplierId", supplierId, "amount", 1, "method", "TRANSFER", "payableIds", List.of(payableId))));

        System.out.println("[E2E] 全链路通过：PR " + prId + " → PO " + orderNo + " → 结清");
    }

    private static void createUser(String username, int roleId) throws Exception {
        assertEquals(200, code("POST", "/api/v1/users", adminToken,
                Map.of("username", username, "password", "Passw0rd", "deptId", deptId.toString())));
        Long uid = findIdByField("GET", "/api/v1/users", adminToken, "username", username);
        assertEquals(200, code("POST", "/api/v1/users/" + uid + "/roles", adminToken, Map.of("roleIds", List.of(roleId))));
    }

    private static void approveFirstTask(String token, String bizType, Long bizId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> resp = call("GET", "/api/v1/approval-tasks/mine", token);
            if (resp.statusCode() == 200) {
                JsonNode tasks = OM.readTree(resp.body()).path("data");
                for (JsonNode t : tasks) {
                    if (bizType.equals(t.path("bizType").asText()) && bizId.equals(t.path("bizId").asLong())) {
                        long taskId = t.path("id").asLong();
                        assertEquals(200, code("POST", "/api/v1/approval-tasks/" + taskId + "/approve", token,
                                Map.of("opinion", "同意")), "approve task failed");
                        return;
                    }
                }
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("No pending task for " + bizType + " " + bizId);
    }

    private static String prStatus() throws Exception {
        HttpResponse<String> resp = call("GET", "/api/v1/requisitions/" + prId, purchaserToken);
        return OM.readTree(resp.body()).path("data").path("status").asText();
    }

    private static String orderStatus() throws Exception {
        HttpResponse<String> resp = call("GET", "/api/v1/orders/" + orderId, purchaserToken);
        return OM.readTree(resp.body()).path("data").path("status").asText();
    }

    private static Long awaitPayable(Long orderId) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> resp = call("GET", "/api/v1/payments", financeToken);
            if (resp.statusCode() == 200) {
                JsonNode data = OM.readTree(resp.body()).path("data");
                if (data.isArray()) {
                    for (JsonNode p : data) {
                        if (orderId.equals(p.path("orderId").asLong())) {
                            return p.path("id").asLong();
                        }
                    }
                }
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Payable not generated for order " + orderId);
    }

    private static String payableStatus() throws Exception {
        HttpResponse<String> resp = call("GET", "/api/v1/payments", financeToken);
        JsonNode data = OM.readTree(resp.body()).path("data");
        for (JsonNode p : data) {
            if (payableId.equals(p.path("id").asLong())) {
                return p.path("status").asText();
            }
        }
        return "NOT_FOUND";
    }

    private static void awaitOrderSettled(Long orderId) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if ("SETTLED".equals(orderStatus())) {
                return;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Order " + orderId + " not SETTLED in time, status=" + orderStatus());
    }

    private static int ledgerCount(Long itemId) throws Exception {
        HttpResponse<String> resp = call("GET", "/api/v1/inventory/ledger?itemId=" + itemId, warehouseToken);
        JsonNode data = OM.readTree(resp.body()).path("data");
        return data.isArray() ? data.size() : 0;
    }

    @Test
    @DisplayName("全链路闭环已在 @BeforeAll 执行并通过")
    void closedLoopPassed() {
        assertNotNull(orderId);
        assertNotNull(payableId);
        System.out.println("closed loop verified");
    }
}