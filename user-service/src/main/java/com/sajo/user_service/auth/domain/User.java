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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
 
    @Builder
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
