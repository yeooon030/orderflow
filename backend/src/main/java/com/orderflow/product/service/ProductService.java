package com.orderflow.product.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ErrorCode;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 정렬을 id ASC로 고정한다. 정렬이 없으면 페이지 간 중복과 누락이 발생할 수 있다.
     * open-in-view가 false이므로 Entity를 DTO로 변환한 뒤 반환한다.
     */
    public Page<ProductResponse> getProducts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return productRepository.findAll(pageRequest).map(ProductResponse::from);
    }

    public ProductResponse getProduct(Long productId) {
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
