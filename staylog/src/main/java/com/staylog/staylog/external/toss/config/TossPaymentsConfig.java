package com.staylog.staylog.external.toss.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 토스 페이먼츠 설정
 */
@ConfigurationProperties(prefix = "toss.payments")
@Getter
@Setter
public class TossPaymentsConfig {

    private String clientKey;      // 클라이언트 키 (프론트엔드용)
    private String secretKey;      // 시크릿 키 (서버용)
    private String webhookSecret;  // 웹훅 시그니처 검증용 보안 키
    private String apiUrl;         // 토스 API URL
    private String successUrl;     // 결제 성공 리다이렉트 URL
    private String failUrl;        // 결제 실패 리다이렉트 URL

    /**
     * Authorization 헤더용 인코딩된 시크릿 키 반환
     * Basic Auth 형식: Base64(secretKey + ":")
     */
    public String getEncodedSecretKey() {
        return Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}
