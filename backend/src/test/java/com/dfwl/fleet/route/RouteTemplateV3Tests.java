package com.dfwl.fleet.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.service.RouteTemplateService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3templatedb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class RouteTemplateV3Tests {
    @Autowired RouteTemplateService templates;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM route_template");
        jdbc.update("DELETE FROM product WHERE id = 901");
        jdbc.update("INSERT INTO product (id, name, enabled) VALUES (901, '砂石', 1)");
    }

    @Test
    void createsUpdatesAndDisablesWithoutChangingStableIdentity() {
        var created = templates.create(request(null, "100.00"), 1);
        assertThat(created.stableCode()).matches("L\\d{7}");
        assertThat(created.customerName()).isEqualTo("昆明客户");
        assertThat(created.defaultUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(created.enabled()).isTrue();

        var updated = templates.update(created.id(), request(created.version(), "110.00"), 1);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.stableCode()).isEqualTo(created.stableCode());
        assertThat(updated.defaultUnitPrice()).isEqualByComparingTo("110.00");
        assertThat(templates.setEnabled(created.id(), updated.version(), false, 1).enabled()).isFalse();
        assertThatThrownBy(() -> templates.update(created.id(), request(created.version(), "120.00"), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE business_type='ROUTE_TEMPLATE'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void identicalBusinessFieldsAreRejectedForCreateAndUpdateEvenWithDifferentNameOrStatus() {
        var first = templates.create(request(null, "100.00"), 1);
        assertThatThrownBy(() -> templates.create(new RouteTemplateRequest("另一名称", "昆明客户", 901L,
                "昆明", "曲靖", new BigDecimal("100.0"), new BigDecimal("20.00"),
                new BigDecimal("30.00"), "另一备注", null), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("线路模板已存在");
        var second = templates.create(request(null, "101.00"), 1);
        assertThat(second.stableCode()).matches("L\\d{7}").isNotEqualTo(first.stableCode());
        assertThatThrownBy(() -> templates.update(second.id(), request(second.version(), "100.00"), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("线路模板已存在");
        var disabled = templates.setEnabled(first.id(), first.version(), false, 1);
        assertThatThrownBy(() -> templates.create(request(null, "100.00"), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("线路模板已存在");
        assertThat(templates.update(disabled.id(), new RouteTemplateRequest("改名", "昆明客户", 901L,
                "昆明", "曲靖", new BigDecimal("100.00"), new BigDecimal("20.00"),
                new BigDecimal("30.00"), "备注修改", disabled.version()), 1).stableCode())
                .isEqualTo(first.stableCode());
    }

    @Test
    void disabledProductIsRejected() {
        jdbc.update("UPDATE product SET enabled=0 WHERE id=901");
        assertThatThrownBy(() -> templates.create(new RouteTemplateRequest("备用", "昆明客户", 901L,
                "昆明", "曲靖", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null), 1))
                .isInstanceOf(BusinessException.class);
    }

    private RouteTemplateRequest request(Integer version, String price) {
        return new RouteTemplateRequest("昆明至曲靖", "昆明客户", 901L,
                "昆明", "曲靖", new BigDecimal(price), new BigDecimal("20.00"),
                new BigDecimal("30.00"), "测试模板", version);
    }
}
