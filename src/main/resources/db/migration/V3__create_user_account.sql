-- 사용자 계정 테이블 (ADR-0010: 자체 아이디·비밀번호 + 서버 세션 쿠키)
-- 비밀번호는 BCrypt 해시만 저장한다. 평문 비밀번호 컬럼은 없다.
CREATE TABLE user_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(20)  NOT NULL,              -- 로그인 아이디(영문 소문자·숫자·밑줄)
    password_hash VARCHAR(100) NOT NULL,              -- BCrypt 해시
    display_name  VARCHAR(20)  NOT NULL,              -- 화면 표시 이름
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_login_id UNIQUE (login_id)
);
