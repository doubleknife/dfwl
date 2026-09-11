package com.dfwl.fleet.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTests {

    @Test
    void successUsesStableCode() {
        ApiResponse<String> response = ApiResponse.success("ok", "request-1");

        assertThat(response.code()).isEqualTo("0");
        assertThat(response.message()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.requestId()).isEqualTo("request-1");
    }
}

