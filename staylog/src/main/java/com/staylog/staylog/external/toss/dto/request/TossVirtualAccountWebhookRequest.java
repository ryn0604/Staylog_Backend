package com.staylog.staylog.external.toss.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.staylog.staylog.external.toss.dto.response.TossVirtualAccountResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Toss v2 가상계좌 웹훅 요청 DTO
 * - v2 가상계좌 입금 확인 시 Toss에서 전송하는 웹훅 페이로드
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossVirtualAccountWebhookRequest {

    private String createdAt;
    private String secret;
    private String orderId;
    private String status;
    private String transactionKey;

    // 컨트롤러 코드 호환용
    public TossVirtualAccountResponse toResponse() {
        TossVirtualAccountResponse data = new TossVirtualAccountResponse();
        data.setOrderId(orderId);
        data.setStatus(status);
        data.setSecret(secret);
        // transactionKey는 필요하면 새 필드로 TossVirtualAccountResponse에 추가 가능
        return data;
    }
}
