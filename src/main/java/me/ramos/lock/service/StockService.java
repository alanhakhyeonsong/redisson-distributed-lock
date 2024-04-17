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
