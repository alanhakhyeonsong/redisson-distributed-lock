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
     * AOP로 분산락을 적용할 때 Propagation 옵션을 별도로 주지 않고 AOP의 Order를 가장 빠르게 부여한 케이스
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

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/efb03dd3-059d-4018-8465-b19719892f6a)

## 공식 문서를 살펴봅니다.
- [Using @Transactional - docs.spring.io](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
  - `@Transactional`이 AOP 상 적용되는 순서의 default 값

![image](https://github.com/alanhakhyeonsong/redisson-distributed-lock/assets/60968342/5c4b5529-3db0-45da-b155-b5ab4fe8699d)

- [Advice Ordering - docs.spring.io](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#aop-ataspectj-advice-ordering)