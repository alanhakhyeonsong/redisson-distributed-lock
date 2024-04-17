package me.ramos.lock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.ramos.lock.domain.Stock;
import me.ramos.lock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Redisson 분산락을 적용한 재고 감소 예제 로직을 테스트
 * - 단, 전체 테스트를 한 번에 실행하지 않고 각 테스트 메서드 하나 씩 실행해야 합니다. (테스트 격리가 안되어있음)
 */
@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        Stock stock = new Stock(1L, 100L);
        stockRepository.saveAndFlush(stock);
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @DisplayName("동시 요청을 수행할 때, Propagation.REQUIRED 기본 옵션이 적용된 트랜잭션에 분산락 로직이 묶여서 실행될 경우, 동시성 이슈가 발생한다.")
    @Test
    void decreaseV1() throws Exception {
        //given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        //when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decreaseV1("stock-ex-1", 1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        //then
        Stock stock = stockRepository.findById(1L).orElseThrow();

        assertThat(stock.getQuantity()).isZero();
    }

    @DisplayName("동시 요청을 수행할 때, Propagation.REQUIRES_NEW 옵션이 적용된 별도의 트랜잭션에 분산락 로직이 묶여서 실행될 경우, 동시성 이슈가 발생하지 않는다.")
    @Test
    void decreaseV2() throws Exception {
        //given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        //when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decreaseV2("stock-ex-2", 1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        //then
        Stock stock = stockRepository.findById(1L).orElseThrow();

        assertThat(stock.getQuantity()).isZero();
    }

    @DisplayName("동시 요청을 수행할 때, Propagation.REQUIRED 기본 옵션이 적용된 트랜잭션에 분산락 로직이 묶여서 실행되지만, 분산락 AOP의 Order는 가장 우선순위로 부여한 케이스. 성공은 하지만, 근본적으로 문제를 해결한지는 모르겠음.")
    @Test
    void decreaseV3() throws Exception {
        //given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        //when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decreaseV3("stock-ex-3", 1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        //then
        Stock stock = stockRepository.findById(1L).orElseThrow();

        assertThat(stock.getQuantity()).isZero();
    }
}