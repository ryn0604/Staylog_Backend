package com.staylog.staylog.domain.search.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccomListResponse {
    private Long accommodationId;
    private String accommodationName;
    private String regionName;
    private Integer minCapacity; //쿼리에서 최소인원 2로 설정돼 있음.
    private Integer maxCapacity;
    private Integer totalCapacity; // maxadult + maxchildren + maxinfant
    private Integer basePrice;
    private Integer reservationCount;

    //대표이미지 캐러셀 여러개일 수 있음.
    private String imageUrl;
}
