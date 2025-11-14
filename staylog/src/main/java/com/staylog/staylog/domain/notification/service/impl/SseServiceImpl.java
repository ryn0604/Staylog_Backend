package com.staylog.staylog.domain.notification.service.impl;

import com.staylog.staylog.domain.notification.dto.response.NotificationResponse;
import com.staylog.staylog.domain.notification.dto.response.NotificationUserMapping;
import com.staylog.staylog.domain.notification.mapper.NotificationMapper;
import com.staylog.staylog.domain.notification.service.SseService;
import com.staylog.staylog.global.annotation.CommonRetryable;
import com.staylog.staylog.global.event.NotificationCreatedAllEvent;
import com.staylog.staylog.global.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseServiceImpl implements SseService {

    // Emitter 타임아웃 시간 (30분)
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 30;

    // UserId, CopyOnWriteArrayList<SseEmitter>를 key-value 형태로 가지는 맵
    // 동시성 처리를 위한 ConcurrentHashMap
    // 한 명의 유저가 여러 기기로 접속하는 것을 관리
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    private final NotificationMapper notificationMapper;

    /**
     * 클라이언트 구독 메서드
     * Emitter의 등록과 제거 예약을 담당
     * @param userId 인증된 사용자 PK
     * @return SseEmitter
     * @author 이준혁
     */
    @Override
    public SseEmitter subscribe(Long userId) {
        log.info("SSE 연결 시작. userId: {}", userId);

        // SseEmitter를 생성
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // emitter를 제거하는 cleanup 메서드 정의
        Runnable cleanup = () -> { // 나중에 cleanup을 실행하면 아래의 블럭이 실행된다.
            CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);
            if (emitters != null) {
                // CopyOnWriteArrayList 자료구조는 remove가 발생하면 내부적으로 배열을 통째로 복사한 후 emitter가 제외된 새 배열을 만든다.
                // 복사하는 방식 덕분에 다른 스레드에서 순회하는 중에 remove가 일어나도 에러가 터지지 않는다.
                // 비싼 대신 안전한 방식
                emitters.remove(emitter);

                if (emitters.isEmpty()) {
                    emitterMap.remove(userId);
                }
            }
        };

        // 완료, 타임아웃, 에러 발생 시 해당 emitter 제거
        // 해당 메서드가 실행되면 미리 정의해둔 cleanup 메서드가 실행되도록 콜백을 등록한다.
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run()); // IOException이 발생하면 실행될 콜백 -> 앞으로 remove 없이 알아서 cleanup 해준다.

        // userId 키값으로 emitterMap에서 찾아서 있다면 가져오고 없다면 새로운 CopyOnWriteArrayList를 생성
        // 키가 있다면 이미 해당 유저가 어떤 기기로 접속하고 있다가 다른 기기로 추가 접속한 것
        // 두 기기의 연결을 보존하기 위함
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());

        // 가져오거나 생성한 emitters에 emitter 추가
        emitters.add(emitter);

        try { // 연결 성공 및 더미 데이터 전송
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE Connected! (userId: " + userId + ")"));
            log.info("SSE 연결 성공. userId: {}, eventName: {}", userId, "connect");
        } catch (IOException e) {
            log.warn("알림 채널 구독 실패 - userId={}", userId);
            emitter.completeWithError(e);
        }
        return emitter;
    }


    /**
     * 특정 유저에게 알림 푸시 메서드
     * 알림 저장 이벤트를 받아 실행된다.
     *
     * @param event -> userId, notificationResponse
     * @author 이준혁
     * @apiNote NotificationServiceImpl에서 호출하여 사용한다.
     * IOException은 해당 메서드에서 처리
     */
    @TransactionalEventListener
    @CommonRetryable // 실패시 재시도
    public void sendNotification(NotificationCreatedEvent event) {
        long userId = event.getUserId();
        log.info("알림 발송 이벤트 확인 userId: {}", userId);

        // Map에서 해당 유저의 emitter를 꺼내기
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);

        // emitters가 null이거나 비어있다면
        if (emitters == null || emitters.isEmpty()) {
            log.info("존재하지 않는 emitter. userId: {}", userId);
            return;
        }

        // for-each로 emitters를 순환하며 알림 PUSH
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("new-notification")
                        .data(event.getNotificationResponse()));
                log.info("알림 푸시 완료. userId: {}, eventName: {}", userId, "new-notification");
            } catch (IOException e) {
                // 클라이언트가 창을 닫는 등 심각하지 않은 예외 -> throw없이 emitter만 제거
                // emitters.remove 할 필요없이 onError() 콜백이 자동으로 호출되어 cleanup을 수행한다.
                log.warn("유효하지 않은 Emitter -> 삭제 처리. userId={}", userId);
                // throw new BusinessException(ErrorCode.NOTIFICATION_EMITTER_NOT_FOUND);
            }
        }
    }



    /**
     * 일괄 저장된 알림 중 현재 접속자에게만 개별 푸시
     *
     * @param event -> notificationResponse 알림 데이터
     * @author 이준혁
     */
    @TransactionalEventListener
    @CommonRetryable
    public void pushBatchNotifications(NotificationCreatedAllEvent event) {
        log.info("일괄 알림 발송 이벤트 확인 batchId: {}", event.getBatchId());

        // 현재 접속 중인 유저 ID 목록
        Set<Long> connectedUserIds = getConnectedUserIds();
        if (connectedUserIds.isEmpty()) {
            log.info("접속 중인 유저가 없으므로 Batch SSE 푸시를 스킵합니다. batchId: {}", event.getBatchId());
            return;
        }

        // DB에서 방금 저장한 알림 중 접속 중인 유저의 것만 조회
        List<NotificationUserMapping> notisToPush = notificationMapper.findByBatchIdAndUserIds(event.getBatchId(), connectedUserIds);
        if (notisToPush == null || notisToPush.isEmpty()) {
            log.warn("알림 데이터 조회 실패: 접속한 유저가 없거나, 알림 정보를 찾을 수 없습니다.");
//            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
            return; // 정상적인 동작이므로 throw 대신 return
        }

        // 여러번 사용하므로 미리 꺼내놓기
        NotificationResponse responseTemplate = event.getNotificationResponse();

        for (NotificationUserMapping noti : notisToPush) {
            // 동시성 문제 발생 위험 -> 객체를 변경하지 않고 새로운 객체를 만들어서 PUSH
            // event.getNotificationResponse().setNotiId(noti.getNotiId());

            // notiId와 targetId만 DB에서 꺼내온 것으로 빌드하여 PUSH할 객체 생성
            NotificationResponse notificationResponse = NotificationResponse.builder()
                    .notiId(noti.getNotiId()) // (From DB)
                    .targetId(noti.getUserId()) // (From DB)
                    .details(responseTemplate.getDetails()) // (From Event Template)
                    .isRead(responseTemplate.getIsRead()) // (From Event Template)
                    .createdAt(responseTemplate.getCreatedAt()) // (From Event Template)
                    .build();

            long userId = noti.getUserId();
            // Map에서 해당 유저의 emitter를 꺼내기
            CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);

            if (emitters == null || emitters.isEmpty()) {
                log.info("존재하지 않는 emitter. userId: {}", userId);
                continue;
            }

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("new-notification")
                            .data(notificationResponse));
                    log.info("알림 푸시 완료. userId: {}, eventName: {}", userId, "new-notification");
                } catch (IOException e) {
                    // 클라이언트가 창을 닫는 등 심각하지 않은 예외 -> throw없이 emitter만 제거
                    // emitters.remove 할 필요없이 onError() 콜백이 자동으로 호출되어 cleanup을 수행한다.
                    log.warn("유효하지 않은 Emitter -> 삭제 처리. userId={}", userId);
                    // throw new BusinessException(ErrorCode.NOTIFICATION_EMITTER_NOT_FOUND);
                }
            }
        }
        log.info("일괄 알림 푸시 완료. eventName: {}", "new-notification");
    }



    private Set<Long> getConnectedUserIds() {
        // emitters 맵의 키셋(KeySet)을 반환합니다.
        // Set.copyOf()를 사용하면 불변(immutable) Set으로 복사해서 반환하므로,
        // 반환된 Set을 조작해도 원본 emitters 맵에 영향이 없어 더 안전합니다.
        return Set.copyOf(emitterMap.keySet());
    }


    /**
     * SSE 연결 타임아웃 방지용 Heartbeat 전송
     * Nginx의 리버스 프록시 타임아웃 시간을 고려한 Heartbeat
     * @author 이준혁
     * 20초마다 주석(comment)를 전송하여 타임아웃을 방지
     */
    @Async("asyncTaskExecutor")
    @Scheduled(fixedRate = 20000) // 20초
    public void sendHeartbeat() {
        emitterMap.forEach((userId, emitters) -> {
            for(SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    log.debug("SSE의 심장이 도키도키...(Heartbeat) userId: {}", userId);
                } catch (IOException e) {
                    // 클라이언트가 창을 닫는 등 심각하지 않은 예외 -> throw없이 emitter만 제거
                    // emitters.remove 할 필요없이 onError() 콜백이 자동으로 호출되어 cleanup을 수행한다.
                    log.warn("Heartbeat 전송 실패로 SSE 연결 종료. userId: {}", userId);
                    // throw를 던지지 않고 해당 유저만 조용히 처리
                    // throw new RuntimeException(e);
                }
            }
        });
    }


    /**
     * 애플리케이션 종료 시 연결된 SSE Emitter 연결을 정리하는 메서드
     *
     * @author 이준혁
     */
    @EventListener(ContextClosedEvent.class)
    public void handleContextClosedEvent() {
        log.info("Spring Context Closed: 모든 SSE Emitter 연결을 종료합니다.");
        emitterMap.forEach((userId, emitters) -> {
            for(SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("SSE Emitter 종료 중 오류 발생. userId: {}", userId, e);
                }
            }
        });
        emitterMap.clear();
        log.info("모든 SSE Emitter 연결 종료 완료.");
    }


}
