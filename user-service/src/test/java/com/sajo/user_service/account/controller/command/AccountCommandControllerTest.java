package com.sajo.user_service.account.controller.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.request.AccountCreateRequest;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.command.AccountCreateFacade;
import com.sajo.user_service.account.service.command.AccountDeleteFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountCommandController.class)
@Import(GlobalExceptionHandler.class)
class AccountCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountCreateFacade accountCreateFacade;

    @MockitoBean
    private AccountDeleteFacade accountDeleteFacade;

    @Test
    @DisplayName("계좌를 생성하면 201과 생성된 계좌 정보를 반환한다")
    void createAccount() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountCreateRequest request =
                new AccountCreateRequest("app-key", "secret-key", "12345678-01", AccountType.REAL);
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        given(accountCreateFacade.createAccount(
                eq(userId), eq("app-key"), eq("secret-key"), eq("12345678-01"), eq(AccountType.REAL)))
                .willReturn(account);

        // when & then
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNo").value("12345678-01"));
    }

    @Test
    @DisplayName("appKey가 비어있으면 400을 반환한다")
    void createAccountBlankAppKey() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountCreateRequest request =
                new AccountCreateRequest("", "secret-key", "12345678-01", AccountType.REAL);

        // when & then
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("계좌번호가 12345678-01 형식이 아니면 400을 반환한다")
    void createAccountInvalidAccountNoFormat() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountCreateRequest request =
                new AccountCreateRequest("app-key", "secret-key", "123456789", AccountType.REAL);

        // when & then
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("이미 계좌가 있는 유저면 409를 반환한다")
    void createAccountConflict() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountCreateRequest request =
                new AccountCreateRequest("app-key", "secret-key", "12345678-01", AccountType.REAL);

        given(accountCreateFacade.createAccount(
                eq(userId), any(), any(), any(), any()))
                .willThrow(new BusinessException(AccountErrorCode.ALREADY_HAS_ACCOUNT));

        // when & then
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0002"));
    }

    @Test
    @DisplayName("계좌를 삭제하면 200을 반환한다")
    void deleteAccount() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(accountDeleteFacade).deleteAccount(userId);
    }

    @Test
    @DisplayName("X-User-Id 헤더 없이 요청하면 400을 반환한다 (Gateway를 거치지 않은 요청)")
    void createAccountWithoutUserIdHeader() throws Exception {
        // given
        AccountCreateRequest request =
                new AccountCreateRequest("app-key", "secret-key", "12345678-01", AccountType.REAL);

        // when & then
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    @DisplayName("삭제할 계좌가 없으면 404를 반환한다")
    void deleteAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND))
                .given(accountDeleteFacade).deleteAccount(userId);

        // when & then
        mockMvc.perform(delete("/api/v1/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }
}
