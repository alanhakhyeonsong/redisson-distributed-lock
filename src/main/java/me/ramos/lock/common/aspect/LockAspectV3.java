package me.ramos.lock.common.aspect;

import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ramos.lock.common.utils.CustomSpringELParser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Redis 분산 락을 적용하기 위한 AOP
 * - 이 Aspect의 적용 대상은 @Transactional의 전파 옵션을 따로 두지 않는 케이스입니다. (REQUIRED)
 * - @Transactional 보다 우선 순위로 AOP가 걸리도록 @Order(-1)을 추가한 케이스
 *
 * @Author HakHyeon Song
 */
@Slf4j
@Aspect
@Order(-1)
@Component
@RequiredArgsConstructor
public class LockAspectV3 {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;

    @Around("@annotation(me.ramos.lock.common.aspect.RedisLockV3)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RedisLockV3 lockV3 = method.getAnnotation(RedisLockV3.class);

        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), lockV3.key());

        // lock 획득
        RLock rLock = redissonClient.getLock(key);

        try {
            // lock의 타임 아웃 체크
            boolean available = rLock.tryLock(lockV3.waitTime(), lockV3.leaseTime(), lockV3.timeUnit());
            if (!available) {
                return false;
            }
            // 실제 비즈니스 로직 수행 (Transaction이 걸린 부분에서 동일한 트랜잭션에 참여한다.)
            return joinPoint.proceed();
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                // lock 해제
                rLock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.info("Redisson Lock Already UnLock {} {}", method.getName(), key);
            }
        }
    }
}
