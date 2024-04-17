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
import org.springframework.stereotype.Component;

/**
 * Redis 분산 락을 적용하기 위한 AOP
 * - 이 Aspect의 적용 대상은 @Transactional의 전파 옵션을 REQUIRES_NEW로 두는 케이스입니다.
 *
 * @Author HakHyeon Song
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LockAspectV2 {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(me.ramos.lock.common.aspect.RedisLockV2)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RedisLockV2 lockV2 = method.getAnnotation(RedisLockV2.class);

        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), lockV2.key());
        // lock 획득
        RLock rLock = redissonClient.getLock(key);

        try {
            // lock의 타임 아웃 체크
            boolean available = rLock.tryLock(lockV2.waitTime(), lockV2.leaseTime(), lockV2.timeUnit());
            if (!available) {
                return false;
            }
            // REQUIRES_NEW 전파
            return aopForTransaction.proceed(joinPoint);
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
