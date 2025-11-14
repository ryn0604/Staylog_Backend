package com.staylog.staylog.domain.home.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staylog.staylog.domain.home.dto.request.HomeAccommodationListRequest;
import com.staylog.staylog.domain.home.dto.response.HomeAccommodationListResponse;
import com.staylog.staylog.domain.home.service.HomeService;
import com.staylog.staylog.global.common.code.SuccessCode;
import com.staylog.staylog.global.common.response.SuccessResponse;
import com.staylog.staylog.global.common.util.MessageUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class HomeController {
	
	private final HomeService homeService;
	private final MessageUtil messageUtil;
	
	@GetMapping("/home")
	public ResponseEntity<SuccessResponse<List<HomeAccommodationListResponse>>> homeAccommodation(@ModelAttribute HomeAccommodationListRequest req){
		
		log.info("숙소 리스트 요청");
		

		List<HomeAccommodationListResponse> homeAccommodation = homeService.homeAccommodationList(req);
		
		String message = messageUtil.getMessage(SuccessCode.ACCOMMODATION_LIST_FOUND.getMessageKey());
		
		String code = SuccessCode.SUCCESS.name();
		
		SuccessResponse<List<HomeAccommodationListResponse>> success = SuccessResponse.of(code, message, homeAccommodation);
		
		return ResponseEntity.ok(success);
	}
}
