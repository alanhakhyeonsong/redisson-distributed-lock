# redisson-distributed-lock
- Java 17
- Spring Boot 3
- Redis, MySQL (Docker-Compose)

## Quick Start
```bash
# 로컬에서 Docker Engine을 실행한 뒤,
# 이 프로젝트의 root directory에서 다음 명령어를 실행합니다.
docker-compose up -d
```

`StockServiceTest` 내의 Test Case를 독립적으로 수행하여 테스트합니다.

테스트 대상 클래스는 다음과 같습니다.

```java
package me.ramos.lock.service;

import lombok.RequiredArgsConstructor;
import me.ramos.lock.common.aspect.RedisLockV1;
import me.ramos.lock.common.aspect.RedisLockV2;
import me.ramos.lock.common.aspect.RedisLockV3;
import me.ramos.lock.domain.Stock;
import me.ramos.lock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 관리 Service Class
 *
 * @Author HakHyeon Song
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    /**
     * AOP로 분산락을 적용할 때 Propagation 옵션과 Order 옵션을 별도로 주지 않은 케이스
     *
     * @param lockName Redis Lock Name
     * @param id       재고의 ID
     * @param quantity 감소할 재고
     */
    @RedisLockV1(key = "#lockName")
    public void decreaseV1(String lockName, Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    /**
     * AOP로 분산락을 적용할 때 Propagation 옵션을 REQUIRES_NEW로 지정한 케이스 (AOP의 Order 부여 X)
     *
     * @param lockName Redis Lock Name
     * @param id       재고의 ID
     * @param quantity 감소할 재고
     */
    @RedisLockV2(key = "#lockName")
    public void decreaseV2(String lockName, Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    /**
     * AOP로 분산락을 적용할 때 Propagation 옵션을 별도로 주지 않고 분산락 AOP의 Order를 가장 빠르게 부여한 케이스
     *
     * @param lockName Redis Lock Name
     * @param id       재고의 ID
     * @param quantity 감소할 재고
     */
    @RedisLockV3(key = "#lockName")
    public void decreaseV3(String lockName, Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}
```

`AopForTransaction`는 AOP로 분산락을 적용 시, `Propagation.REQUIRES_NEW` 옵션을 부여하여 분산락의 잠금과 해제를 별도의 트랜잭션으로 분리하기 위한 클래스입니다.

## Test Case
- `decreaseV1`
  - "동시 요청을 수행할 때, Propagation.REQUIRED 기본 옵션이 적용된 트랜잭션에 분산락 로직이 묶여서 실행될 경우, 동시성 이슈가 발생한다."

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/d38a8d17-0362-4425-b5c1-062c3e472610)

- `decreaseV2`
  - "동시 요청을 수행할 때, Propagation.REQUIRES_NEW 옵션이 적용된 별도의 트랜잭션에 분산락 로직이 묶여서 실행될 경우, 동시성 이슈가 발생하지 않는다."

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/dc9e9203-06ab-4073-a463-c21b288297e4)

- `decreaseV3`
  - "동시 요청을 수행할 때, Propagation.REQUIRED 기본 옵션이 적용된 트랜잭션에 분산락 로직이 묶여서 실행되지만, 분산락 AOP의 Order는 가장 우선순위로 부여한 케이스. 성공은 하지만, 근본적으로 문제를 해결한지는 모르겠음."
  - 분산 락이 우선 순위를 가지도록 `@Order(-1)`을 설정했기 때문에, 분산 락이 트랜잭션보다 먼저 실행되어 동작한다. 추측상, 이로 인해 병렬로 실행되는 각각의 요청이 분산 락을 획득한 상태에서 트랜잭션 내의 작업을 순차적으로 실행하게 되므로 이러한 상황에서는 테스트가 성공할 수 있지 않을까 한다.

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/efb03dd3-059d-4018-8465-b19719892f6a)

## 공식 문서를 살펴봅니다.
- [Using @Transactional - docs.spring.io](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
  - `@Transactional`이 AOP 상 적용되는 순서의 default 값

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/5c4b5529-3db0-45da-b155-b5ab4fe8699d)

- [Advice Ordering - docs.spring.io](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#aop-ataspectj-advice-ordering)

## 트랜잭션 전파 속성
- `REQUIRED` : 트랜잭션이 있는 경우 참여하고 없으면 새 트랜잭션을 생성하며 propagation 설정이 없는 경우의 기본값.
- `REQUIRES_NEW` : 항상 새 트랜잭션을 만들고 트랜잭션이 있다면 끝날 때까지 일시중지한다.
- `NESTED` : 기존 트랜잭션과 중첩된 트랜잭션을 생성하고, 없다면 새로 트랜잭션을 생성한다.
- `SUPPORTS` : 존재하는 트랜잭션이 있다면 지원하고, 없으면 트랜잭션 없이 메서드만 실행한다.
- `MANDATORY` : 반드시 트랜잭션이 존재해야 하는 유형으로 없으면 예외(`ThrowIllegalTransactionStateException`)가 발생.
- `NOT_SUPPORTED` : 트랜잭션이 있어도 중단되며, 트랜잭션을 지원하지 않는다.
- `NEVER` : 트랜잭션이 존재하면 예외(`ThrowIllegalTransactionStateException`)가 발생.