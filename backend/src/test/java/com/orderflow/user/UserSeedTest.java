package com.orderflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.user.entity.User;
import com.orderflow.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * [테스트 목적] 테스트용 User Seed가 정상 적재되고, Seed 이후 JPA 저장이 PK 충돌 없이 동작하는지 확인
 *
 * [테스트 방식]
 *  - @Sql로 Seed를 적재한 뒤 @Transactional로 롤백하므로 다른 테스트에 데이터가 남지 않는다.
 *  - Sequence의 INCREMENT BY(50)와 JPA allocationSize(50) 일치는 설정만으로는 확인할 수 없다.
 *    Seed가 id를 직접 지정하기 때문에, 실제로 저장해 봐야 충돌 여부를 알 수 있다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/user-seed.sql")
class UserSeedTest {

    private static final long SEED_USER_COUNT = 100L;

    /**
     * users_seq의 INCREMENT BY이자 User의 @SequenceGenerator allocationSize.
     */
    private static final int ALLOCATION_SIZE = 50;

    @Autowired
    private UserRepository userRepository;

    @Test
    void Seed는_User_100명을_생성한다() {
        assertThat(userRepository.count()).isEqualTo(SEED_USER_COUNT);
        assertThat(userRepository.findById(1L)).isPresent();
        assertThat(userRepository.findById(SEED_USER_COUNT)).isPresent();
    }

    @Test
    void Seed_이후_JPA로_저장해도_PK가_충돌하지_않는다() {
        User saved = userRepository.save(new User("신규 사용자"));
        userRepository.flush();

        assertThat(saved.getId()).isGreaterThan(SEED_USER_COUNT);
        assertThat(userRepository.count()).isEqualTo(SEED_USER_COUNT + 1);
    }

    /**
     * 1건만 저장하면 Hibernate가 할당해 둔 첫 블록 안에서 끝나므로
     * INCREMENT BY와 allocationSize가 어긋나도 드러나지 않을 수 있다.
     * 블록 경계를 넘겨 재할당이 일어나는 지점까지 저장해야 두 값의 불일치가 나타난다.
     */
    @Test
    void 할당_블록_경계를_넘겨_저장해도_id가_고유하고_Seed와_겹치지_않는다() {
        int saveCount = ALLOCATION_SIZE + 10;
        List<Long> ids = new ArrayList<>();

        for (int i = 1; i <= saveCount; i++) {
            ids.add(userRepository.save(new User("연속 사용자" + i)).getId());
        }
        userRepository.flush();

        assertThat(ids).hasSize(saveCount);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allMatch(id -> id > SEED_USER_COUNT);
        assertThat(userRepository.count()).isEqualTo(SEED_USER_COUNT + saveCount);
    }
}
