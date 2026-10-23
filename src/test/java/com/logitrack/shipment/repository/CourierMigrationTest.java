package com.logitrack.shipment.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CourierMigrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test void upgradesV1PreservingUuidsCouriersShipmentsAndEveryHistoryColumn() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var sql = connection.createStatement()) {
            String audit = ", NOW(6), NOW(6), 'legacy', 'legacy', 7";
            sql.executeUpdate("INSERT INTO customers VALUES (UNHEX(REPEAT('11',16)), 'Cliente', 'legacy@test.com','123'" + audit + ")");
            sql.executeUpdate("INSERT INTO couriers VALUES (UNHEX(REPEAT('22',16)), 'Courier', 'MOTO','ACTIVO'" + audit + ")");
            sql.executeUpdate("INSERT INTO shipments VALUES (UNHEX(REPEAT('33',16)), 'LT-0123456789', UNHEX(REPEAT('11',16)), "
                    + "UNHEX(REPEAT('22',16)), 'A','1','C','1000','B','2','D','2000',2.5,30,20,40,2200,'PENDIENTE'" + audit + ")");
            sql.executeUpdate("INSERT INTO shipment_status_history VALUES (UNHEX(REPEAT('44',16)),UNHEX(REPEAT('33',16)),"
                    + "NULL,'PENDIENTE',NOW(6),'Alta previa'" + audit + ")");
            var before = snapshot(connection);
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
            assertThat(snapshot(connection)).isEqualTo(before);
            sql.executeUpdate("UPDATE shipments SET courier_id=UNHEX(REPEAT('55',16))");
            assertThat(sql.executeUpdate("DELETE FROM couriers")).isEqualTo(1);
            try (var rows = sql.executeQuery("SELECT HEX(courier_id) FROM shipments")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("55".repeat(16));
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM shipment_status_history")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    Map<String,List<List<String>>> snapshot(Connection connection) throws SQLException {
        Map<String,List<List<String>>> snapshot = new LinkedHashMap<>();
        for (String table : List.of("customers", "couriers", "shipments", "shipment_status_history")) {
            var rows = new ArrayList<List<String>>();
            try (var sql = connection.createStatement(); var rs = sql.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                while (rs.next()) {
                    var row = new ArrayList<String>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object value = rs.getObject(i);
                        row.add(value instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : String.valueOf(value));
                    }
                    rows.add(row);
                }
            }
            snapshot.put(table, rows);
        }
        return snapshot;
    }
}
