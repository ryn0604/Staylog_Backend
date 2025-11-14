package com.staylog.staylog.domain.board.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시판 관련 Dto
 * 저널 / 리뷰 등 공통으로 사용
 * 게시판 종류는 target_type(enum)으로 관리
 *
 * @author 김지은
 */

@Alias("BoardDto")
@Getter
@Setter
public class BoardDto {

    // 게시글 목록
    // private List<BoardDto> boardList;
    
    // Pagination
    // private Integer totalCount;
    // private Integer offset;
    // private int pageSize;

    // 게시판, 지역 카테고리
    private String boardType;       // 카테고리 (REVIEW, JOURNAL)
    private String regionCode;      // 지역 코드 (SEOUL, BUSAN, ..)(common_code.code_id)
    private String regionName;      // 지역 이름 (서울, 부산..)

    private Long boardId;           // 리뷰 고유번호 (PK)
    private Long accommodationId;   // 숙소 고유번호
    private String accommodationName;  // 숙소 고유번호
    private Long bookingId;         // 예약 고유번호 (UNIQUE)
    private String checkIn;         // 체크인
    private String checkOut;        // 체크아웃
    private Long userId;            // 작성자 고유번호
    private String userNickName;    // 작성자 닉네임
    private String userName;        // 작성자 아이디(영문 or 한글)

    private String imageUrl;    // 썸네일 이미지
    private String title;           // 리뷰 제목
    private String content;         // 리뷰 내용

    private Integer rating;         // 별점 (1~5)
    private Integer viewsCount;     // 조회 수
    private Integer likesCount;     // 좋아요 수

    private LocalDateTime createdAt;    // 작성일
    private LocalDateTime updatedAt;    // 수정일
    private String deleted;             // 삭제 여부 ('Y'/'N')

    private String keyword;         // 검색어
    private String sort;            // latest / likes / views

}

