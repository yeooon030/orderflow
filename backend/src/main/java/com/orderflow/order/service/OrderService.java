package com.orderflow.order.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import com.orderflow.order.dto.OrderCreateRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.OrderSummaryResponse;
import com.orderflow.order.entity.Order;
import com.orderflow.order.entity.OrderItem;
import com.orderflow.order.entity.OrderStatus;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.OrderSpecifications;
import com.orderflow.product.entity.Product;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.stock.entity.Stock;
import com.orderflow.stock.repository.StockRepository;
import com.orderflow.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            UserRepository userRepository,
            ProductRepository productRepository,
            StockRepository stockRepository,
            OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 주문을 생성한다.
     *
     * User 확인 → Product 확인 → Stock Lock → 재고 확인 → 차감 → Order → OrderItem을
     * 하나의 트랜잭션에서 처리한다. 중간에 예외가 나면 전체가 롤백된다. (BACKEND.md 6, 7)
     *
     * Lock은 이 트랜잭션이 커밋될 때 풀린다. 재고 차감부터 주문 저장까지가 한 트랜잭션이므로
     * 차감만 반영되고 주문이 없는 상태는 생기지 않는다.
     */
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        validateRequest(request);

        Long userId = request.userId();
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        long totalPrice = 0L;
        List<OrderItem> items = new ArrayList<>();

        // 여러 상품을 주문할 때 Lock 획득 순서를 productId 오름차순으로 고정
        for (OrderCreateRequest.Item item : lockOrderOf(request.items())) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            Stock stock = stockRepository.findByProductIdForUpdate(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            stock.decrease(item.quantity());

            items.add(new OrderItem(product.getId(), item.quantity(), product.getPrice()));
            totalPrice += product.getPrice() * item.quantity();
        }

        Order order = new Order(userId, OrderStatus.COMPLETED, totalPrice);
        items.forEach(order::addItem);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    public OrderResponse getOrder(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 주문 목록을 조회한다. userId는 필수이고 status와 기간은 선택이다.
     *
     * endDate는 그날 전체를 포함한다. {@code createdAt <= endDate}로 비교하면
     * 그날 00:00:00 주문만 걸리므로, 다음 날 00:00:00 미만으로 비교한다.
     * endDate=2026-08-23 이면 23일 00:00:00 ~ 23:59:59 주문이 모두 포함된다.
     *
     * 정렬은 createdAt DESC, id DESC로 고정한다.
     * id를 시간순으로 쓰지 않는다. allocationSize가 50이라 각 스레드가 id 블록을 미리 받아
     * 쓰므로, 동시 주문에서는 나중에 만들어진 주문이 더 작은 id를 가질 수 있다.
     * (BACKEND.md 18 — ID를 순번으로 해석하지 않는다)
     *
     * id는 createdAt이 같을 때의 순서를 정하는 역할이다. 이게 없으면 같은 시각의 주문끼리
     * 순서가 정해지지 않아 페이지 간 중복과 누락이 발생할 수 있다.
     */
    public Page<OrderSummaryResponse> getOrders(
            Long userId,
            OrderStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {
        LocalDateTime from = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime to = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Specification<Order> condition = OrderSpecifications.ofUser(userId)
                .and(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.createdOnOrAfter(from))
                .and(OrderSpecifications.createdBefore(to));

        return orderRepository.findAll(condition, pageRequest).map(OrderSummaryResponse::from);
    }

    /**
     * 요청 형태를 검증한다.
     *
     * 값이 아예 없는 것과 값이 잘못된 것을 구분한다.
     * userId나 productId가 없는 것은 "그 리소스가 없다"가 아니라 요청이 덜 온 것이므로
     * 404가 아니라 INVALID_REQUEST다. 존재하지 않는 id를 보낸 경우만 404로 응답한다.
     * INVALID_ORDER_QUANTITY는 수량 값 자체가 잘못된 경우에만 쓴다.
     */
    private void validateRequest(OrderCreateRequest request) {
        if (request.userId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        for (OrderCreateRequest.Item item : request.items()) {
            if (item == null || item.productId() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
            }
        }
    }

    /**
     * 두 요청이 상품 A와 B를 서로 반대 순서로 잠그면 데드락이 발생한다.
     * 모든 트랜잭션이 같은 순서로 잠그면 이 조건 자체가 성립하지 않는다.
     */
    private List<OrderCreateRequest.Item> lockOrderOf(List<OrderCreateRequest.Item> items) {
        return items.stream()
                .sorted(Comparator.comparing(OrderCreateRequest.Item::productId))
                .toList();
    }
}
