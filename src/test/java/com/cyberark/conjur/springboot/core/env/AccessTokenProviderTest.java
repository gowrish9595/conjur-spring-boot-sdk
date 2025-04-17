package com.cyberark.conjur.springboot.core.env;

import com.cyberark.conjur.sdk.AccessToken;
import com.cyberark.conjur.sdk.ApiClient;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.sdk.endpoint.AuthenticationApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AccessTokenProviderTest {


    private AccessTokenProvider accessTokenProvider;

    @BeforeEach
    void setUp() {
        accessTokenProvider = new AccessTokenProvider();
    }

    @Test
    void testGetNewAccessToken() {
        ApiClient apiClient = mock(ApiClient.class);
        AccessToken mockToken = mock(AccessToken.class);
        when(apiClient.getNewAccessToken()).thenReturn(mockToken);

        AccessToken result = accessTokenProvider.getNewAccessToken(apiClient);

        assertSame(mockToken, result);
        verify(apiClient).getNewAccessToken();
    }

    @Test
    void testGetJwtAccessToken_FileNotFound() {
        ApiClient apiClient = mock(ApiClient.class);
        String invalidPath = "invalid/file/path.jwt";

        assertThrows(IOException.class, () -> accessTokenProvider.getJwtAccessToken(apiClient, invalidPath, "authn-jwt-id"));
    }

    @Test
    void testGetJwtAccessToken_Success(@TempDir Path tempDir) throws Exception {
        // Create dummy JWT file
        String jwtContent = "dummy-jwt-token";
        Path jwtFile = tempDir.resolve("jwt.txt");
        Files.write(jwtFile, jwtContent.getBytes(StandardCharsets.UTF_8));

        // Mock ApiClient
        ApiClient apiClient = mock(ApiClient.class);
        when(apiClient.getAccount()).thenReturn("my-account");

        // Mock AuthenticationApi to return fake token JSON
        AuthenticationApi authApiMock = mock(AuthenticationApi.class);
        String tokenJson = "{\"protected\":\"abc\",\"payload\":\"xyz\",\"signature\":\"123\"}";
        when(authApiMock.getAccessTokenViaJWT(eq("my-account"), eq("authn-jwt-id"), eq("api"), eq(jwtContent))).thenReturn(tokenJson);

        // Mock AuthenticationApi constructor using mockito-inline
        try (MockedConstruction<AuthenticationApi> mocked = mockConstruction(AuthenticationApi.class, (mock, context) -> {
            when(mock.getAccessTokenViaJWT(anyString(), anyString(), anyString(), eq(jwtContent))).thenReturn(tokenJson);
        })) {

            AccessTokenProvider provider = new AccessTokenProvider();

            AccessToken result = provider.getJwtAccessToken(apiClient, jwtFile.toString(), "authn-jwt-id");

            assertNotNull(result);
            String expectedHeader = AccessToken.fromEncodedToken(Base64.getEncoder().encodeToString(tokenJson.getBytes(StandardCharsets.UTF_8))).getHeaderValue();
            assertEquals(expectedHeader, result.getHeaderValue());
        }
    }

    @Test
    void testGetJwtAccessToken_ApiExceptionCaught(@TempDir Path tempDir) throws Exception {
        // Prepare fake JWT file
        String jwt = "dummy-jwt-token";
        Path jwtFile = tempDir.resolve("jwt.txt");
        Files.write(jwtFile, jwt.getBytes(StandardCharsets.UTF_8));

        // Mock ApiClient
        ApiClient apiClientMock = mock(ApiClient.class);
        when(apiClientMock.getAccount()).thenReturn("my-account");

        try (MockedConstruction<AuthenticationApi> mocked = mockConstruction(AuthenticationApi.class, (mock, context) -> {
            when(mock.getAccessTokenViaJWT(any(), any(), any(), any())).thenThrow(new ApiException(403, "Forbidden", null, "{\"error\": \"unauthorized\"}"));
        })) {

            AccessTokenProvider provider = new AccessTokenProvider();

            AccessToken token = provider.getJwtAccessToken(apiClientMock, jwtFile.toString(), "authn-jwt-id");

            // Assert: Should return null due to exception
            assertNull(token);
        }
    }


}
