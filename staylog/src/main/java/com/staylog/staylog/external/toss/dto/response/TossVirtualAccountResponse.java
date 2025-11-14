package com.staylog.staylog.external.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Toss Payments 가상계좌 발급 응답 DTO
 * API: POST /v1/virtual-accounts
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossVirtualAccountResponse {

    private String mId;                 // 가맹점 ID
    private String orderId;             // 주문번호
    private String orderName;           // 주문명
    private Long amount;                // 금액
    private String status;              // 상태 (WAITING_FOR_DEPOSIT, DONE, CANCELED, EXPIRED)
    private String secret;              // 가상계좌 조회 시크릿

    // 가상계좌 정보
    @JsonProperty("accountNumber")
    private String accountNumber;       // 계좌번호

    @JsonProperty("bank")
    private String bank;                // 은행명 (예: "신한은행")

    @JsonProperty("customerName")
    private String customerName;        // 고객명

    @JsonProperty("dueDate")
    private OffsetDateTime dueDate;     // 입금 기한

    @JsonProperty("accountHolder")
    private String accountHolder;       // 예금주 (토스페이먼츠)

    // 기타
    private OffsetDateTime createdAt;   // 생성 시각
}
