package com.sajo.user_service.auth.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

@Entity
@Table(name = "p_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    // STRING으로 저장 - ORDINAL은 enum 순서가 바뀌면 기존 데이터의 의미가 조용히 뒤바뀌는
    // 위험이 있다
    // DB 레벨 DEFAULT 필요 - ddl-auto: update가 기존 행이 있는 테이블에 NOT NULL 컬럼을
    // DEFAULT 없이 추가하면 실패한다 (PostgreSQL 제약). 기존 행은 전부 USER로 채워진다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'USER'")
    private Role role;

    // access = PRIVATE 필요 - Lombok의 builder()는 생성자가 private이어도 기본적으로
    // public으로 노출되므로, User.of()를 거치지 않고 builder()를 직접 써서 role(...)을
    // 빠뜨리면 role이 null인 채로 build될 수 있었다. builder() 자체를 private으로 막아
    // 클래스 밖에서는 반드시 of()를 거치도록 강제한다 (@Builder.Default는 클래스 레벨
    // @Builder에서만 동작해서 이 구조 - 생성자 레벨 @Builder - 에는 쓸 수 없다).
    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    public static User of(String email, String password, String name) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(Role.USER) // 회원가입은 항상 일반 사용자로 생성 - 관리자 계정은 별도 절차로 생성
                .build();
    }
}