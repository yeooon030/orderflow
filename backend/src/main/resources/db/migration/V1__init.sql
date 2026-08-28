-- OrderFlow 초기 스키마
--
-- Sequence의 INCREMENT BY는 JPA allocationSize(50)와 일치시킨다.
-- Index는 Execution Plan 분석 후 별도 Migration으로 추가한다.

CREATE SEQUENCE users_seq INCREMENT BY 50;

CREATE TABLE users (
    id         BIGINT       PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE SEQUENCE product_seq INCREMENT BY 50;

CREATE TABLE product (
    id         BIGINT       PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

-- Stock은 별도 id 없이 product_id를 PK이자 FK로 사용하여 Product와 1:1을 강제한다.
CREATE TABLE stock (
    product_id BIGINT    PRIMARY KEY,
    quantity   INTEGER   NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE SEQUENCE orders_seq INCREMENT BY 50;

CREATE TABLE orders (
    id          BIGINT      PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    total_price BIGINT      NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE SEQUENCE order_item_seq INCREMENT BY 50;

CREATE TABLE order_item (
    id         BIGINT  PRIMARY KEY,
    order_id   BIGINT  NOT NULL,
    product_id BIGINT  NOT NULL,
    quantity   INTEGER NOT NULL,
    price      BIGINT  NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);
