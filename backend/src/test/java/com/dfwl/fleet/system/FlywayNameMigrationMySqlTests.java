package com.dfwl.fleet.system;

import com.dfwl.fleet.FleetOpsApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt in only with the disposable local mysqld on port 27386. Never uses application credentials. */
@EnabledIfSystemProperty(named="fleet.test.isolated-mysql", matches="true")
class FlywayNameMigrationMySqlTests {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:27386/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private Connection connect(String database) throws Exception {
        return DriverManager.getConnection(SERVER + database + OPTIONS, "root", "");
    }
    private String createIsolatedDatabase() throws Exception {
        String name = "dfwl_name_fix_" + Long.toUnsignedString(System.nanoTime());
        try (var c = connect("" ); var statement = c.createStatement()) {
            try (var rs = statement.executeQuery("SELECT @@datadir")) {
                rs.next();
                assertThat(rs.getString(1).replace('\\','/').toLowerCase())
                        .contains("/code/dfwl/.runtime/name-refresh-fix/mysql-data");
            }
            statement.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        return name;
    }
    private void execute(String db, String sql) throws Exception {
        try (var c = connect(db)) {
            ScriptUtils.executeSqlScript(c, new EncodedResource(
                    new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
    }
    private String scalar(String db, String sql) throws Exception {
        try (var c = connect(db); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue(); return rs.getString(1);
        }
    }
    private Flyway flyway(String db) {
        return Flyway.configure().dataSource(SERVER+db+OPTIONS,"root","")
                .locations("classpath:db/migration").baselineOnMigrate(true).load();
    }
    private void startBackend(String db) {
        try (var context = new SpringApplicationBuilder(FleetOpsApplication.class).run(
                "--spring.datasource.url="+SERVER+db+OPTIONS,
                "--spring.datasource.username=root", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.flyway.enabled=true", "--spring.flyway.baseline-on-migrate=true",
                "--spring.flyway.baseline-version=1", "--spring.sql.init.mode=never",
                "--server.port=0", "--management.server.port=0",
                "--fleet.storage.local-root=D:/code/dfwl/.runtime/name-refresh-fix/files")) {
            assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("2");
            assertThat(context.getBean(Flyway.class).info().pending()).isEmpty();
        }
    }
    private void seedUsers(String db) throws Exception {
        execute(db, """
                INSERT INTO sys_user(id,phone,password_hash,role_id,status)
                SELECT 901,'19900000901','test-only',id,1 FROM sys_role WHERE role_code='ADMIN';
                INSERT INTO sys_user(id,phone,password_hash,role_id,status)
                SELECT 902,'19900000902','test-only',id,1 FROM sys_role WHERE role_code='DRIVER';
                INSERT INTO sys_user(id,phone,password_hash,role_id,status)
                SELECT 903,'19900000903','test-only',id,1 FROM sys_role WHERE role_code='DRIVER';
                INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status)
                VALUES(902,902,'INTERNAL','自有司机','19900000902','110105199001010117','DEMO-902',1),
                      (903,903,'OUTSOURCED','外协司机','19900000903','110105199001010125','DEMO-903',1);
                """);
    }
    private void verifyUpgradeAndRestart(String db) throws Exception {
        startBackend(db);
        assertThat(scalar(db,"SELECT name FROM sys_user WHERE id=901")).isEqualTo("19900000901");
        assertThat(scalar(db,"SELECT name FROM sys_user WHERE id=902")).isNull();
        assertThat(scalar(db,"SELECT name FROM sys_user WHERE id=903")).isNull();
        assertThat(scalar(db,"SELECT name FROM driver WHERE id=902")).isEqualTo("自有司机");
        assertThat(scalar(db,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1")).isEqualTo("1");
        execute(db,"UPDATE sys_user SET name='人工维护姓名' WHERE id=901");
        startBackend(db);
        assertThat(scalar(db,"SELECT name FROM sys_user WHERE id=901")).isEqualTo("人工维护姓名");
        assertThat(flyway(db).migrate().migrationsExecuted).isZero();
    }
    @Test void freshSnapshotRegistersVersionTwoAndStartsTwiceWithoutV2() throws Exception {
        String db=createIsolatedDatabase();
        String sql=Files.readString(Path.of("../database/database.sql"));
        sql=sql.replaceAll("(?s)CREATE DATABASE IF NOT EXISTS fleet_ops.*?;","")
                .replace("USE fleet_ops;","");
        execute(db,sql);
        assertThat(scalar(db,"SELECT version FROM flyway_schema_history WHERE type='BASELINE'")).isEqualTo("2");
        seedUsers(db); execute(db,"UPDATE sys_user SET name='新库姓名' WHERE id=901");
        startBackend(db); startBackend(db);
        assertThat(scalar(db,"SELECT name FROM sys_user WHERE id=901")).isEqualTo("新库姓名");
        assertThat(scalar(db,"SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo("1");
        assertThat(flyway(db).migrate().migrationsExecuted).isZero();
    }
    @Test void registeredV1UpgradesAndPreservesNamesOnRestart() throws Exception {
        String db=createIsolatedDatabase();
        Flyway.configure().dataSource(SERVER+db+OPTIONS,"root","")
                .locations("classpath:db/migration").target("1").load().migrate();
        seedUsers(db);verifyUpgradeAndRestart(db);
    }
    @Test void oldV1SnapshotWithoutHistoryBaselinesAtOneThenUpgrades() throws Exception {
        String db=createIsolatedDatabase();
        execute(db,Files.readString(Path.of("src/main/resources/db/migration/V1__baseline_schema.sql")));
        seedUsers(db);verifyUpgradeAndRestart(db);
    }
}
