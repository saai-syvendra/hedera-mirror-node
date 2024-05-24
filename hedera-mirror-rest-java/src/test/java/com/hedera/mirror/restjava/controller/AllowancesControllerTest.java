/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatus;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AllowancesControllerTest extends RestJavaIntegrationTest {

    private final DomainBuilder domainBuilder;
    private final NftAllowanceMapper mapper;

    @LocalServerPort
    private int port;

    private String callUri;
    private RestClient restClient;

    @BeforeEach
    void setup() {
        callUri = "http://localhost:%d/api/v1/accounts/{id}/allowances/nfts".formatted(port);
        restClient = RestClient.builder()
                .baseUrl(callUri)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com")
                .build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftAllowancesEntityId(boolean persistEntity) {
        // Given
        var entityBuilder = domainBuilder.entity();
        // Whether or not entity is present in entity table
        var entity = persistEntity ? entityBuilder.persist() : entityBuilder.get();

        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();

        // When
        var result = restClient.get().uri("", allowance1.getOwner()).retrieve().body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
    }

    @Test
    void nftAllowancesEvmAddress() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();

        // When
        var result = restClient
                .get()
                .uri("", DomainUtils.bytesToHex(entity.getEvmAddress()))
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
    }

    @Test
    void nftAllowancesAlias() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();

        // When
        var result = restClient
                .get()
                .uri("", BaseEncoding.base32().omitPadding().encode(entity.getAlias()))
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
    }

    @Test
    void nftAllowancesCors() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();

        // When
        var headers = restClient
                .get()
                .uri("", allowance1.getOwner())
                .retrieve()
                .toEntity(NftAllowancesResponse.class)
                .getHeaders();

        // Then
        assertThat(headers.getAccessControlAllowOrigin()).isEqualTo("*");
    }

    @Test
    void nftAllowancesOrderAsc() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();
        var uriParams = "?account.id=gte:%s&account.id=lt:%s&owner=true&token.id=gt:%s&limit=1&order=asc"
                .formatted(
                        EntityId.of(allowance1.getSpender() - 1),
                        EntityId.of(allowance1.getSpender() + 1),
                        EntityId.of(allowance1.getTokenId() - 1));
        var next = "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&owner=true&token.id=gt:%s&limit=1&order=asc"
                .formatted(
                        allowance1.getOwner(),
                        EntityId.of(allowance1.getSpender()),
                        EntityId.of(allowance1.getTokenId()));

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1), next));
    }

    @Test
    void nftAllowancesAllRangeConditions() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();
        var uriParams =
                "?account.id=gte:%s&account.id=lte:%s&owner=true&token.id=gt:%s&token.id=lt:%s&limit=1&order=desc"
                        .formatted(
                                EntityId.of(allowance1.getSpender()),
                                EntityId.of(allowance2.getSpender()),
                                EntityId.of(allowance1.getTokenId()),
                                EntityId.of(allowance2.getTokenId() + 1));
        var next = "/api/v1/accounts/%s/allowances/nfts?account.id=lte:%s&owner=true&token.id=lt:%s&limit=1&order=desc"
                .formatted(
                        allowance1.getOwner(),
                        EntityId.of(allowance2.getSpender()),
                        EntityId.of(allowance2.getTokenId()));

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), next));
    }

    @Test
    void nftAllowancesNoOperators() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();
        var uriParams = "?account.id={account.id}&limit=1&order=asc";
        var nextLink = "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&limit=1&order=asc&token.id=gt:%s"
                .formatted(
                        allowance2.getOwner(),
                        EntityId.of(allowance2.getSpender()),
                        EntityId.of(allowance2.getTokenId()));

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance2.getOwner(), EntityId.of(allowance2.getSpender()))
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), nextLink));
    }

    @Test
    void nftAllowancesOrderDesc() {
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();
        var uriParams = "?account.id=gte:%s&owner=true&token.id=gt:%s&limit=1&order=desc"
                .formatted(EntityId.of(allowance2.getSpender()), EntityId.of(allowance2.getTokenId() - 1));
        var nextLink =
                "/api/v1/accounts/%s/allowances/nfts?account.id=lte:%s&owner=true&token.id=lt:%s&limit=1&order=desc"
                        .formatted(
                                allowance2.getOwner(),
                                EntityId.of(allowance2.getSpender()),
                                EntityId.of(allowance2.getTokenId()));

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), nextLink));
    }

    @Test
    void nftAllowancesOwnerFalse() {
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.spender(entity.getId()).approvedForAll(true))
                .persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.spender(allowance1.getSpender()).approvedForAll(true))
                .persist();

        var uriParams = "?account.id=gte:%s&owner=false&token.id=gt:%s&limit=1&order=asc"
                .formatted(EntityId.of(allowance1.getOwner()), EntityId.of(allowance1.getTokenId() - 1));
        var next = "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&owner=false&token.id=gt:%s&limit=1&order=asc"
                .formatted(
                        allowance1.getSpender(),
                        EntityId.of(allowance1.getOwner()),
                        EntityId.of(allowance1.getTokenId()));

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getSpender())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1), next));
    }

    @Test
    void nftAllowancesEmptyNextLink() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.spender(entity.getId()).approvedForAll(true))
                .persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()).approvedForAll(true))
                .persist();
        var uriParams = "?account.id=gte:0.0.5000&owner=true&token.id=gt:0.0.5000&limit=1&order=asc";

        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getSpender())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.,0.0.1002,0.0.2000,false,2,asc",
        "0.0.1001,1.2.3.4,0.0.2000,false,2,asc",
        "0.65537.1001,1.2.3,0.0.2000,false,2,asc",
        "0.0.-1001,1.2.3,0.0.2000,false,2,asc",
        "0.0.1001,0.0.2000,1.2.3.4,false,2,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,-1,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,111,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,3,ttt",
        "0.0.1001,gee:0.0.3000,0.0.2000,false,3,asc",
        "null,gte:0.0.3000,0.0.2000,false,3,asc",
        "0.0.4294967296,gt:0.0.3000,gte:0.0.3000,false,3,asc",
        "9223372036854775807,0.0.3000,0.0.2000,false,3,asc",
        "0x00000001000000000000000200000000000000034,0.0.3000,0.0.2000,false,3,asc"
    })
    void nftAllowancesInvalidParams(
            String id, String accountId, String tokenId, String owner, String limit, String order) {
        // Given
        var uriParams = "?account.id={accountId}&owner={owner}&token.id={token.id}&limit={limit}&order={order}";

        // When
        ThrowingCallable callable = () -> restClient
                .get()
                .uri(uriParams, id, accountId, owner, tokenId, limit, order)
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        validateError(callable, HttpClientErrorException.BadRequest.class, "Bad Request", "Bad Request");
    }

    @Test
    void nftAllowancesUnsupportedMediaException() {
        // When
        ThrowingCallable callable =
                () -> restClient.post().uri(callUri, "0.0.1000").retrieve().body(NftAllowancesResponse.class);

        // Then
        validateError(
                callable, HttpClientErrorException.MethodNotAllowed.class, "Method Not Allowed", "Method Not Allowed");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.0x000000000000000000000000000000000186Fb1b",
                "0.0.0x000000000000000000000000000000000186Fb1b",
                "0x000000000000000000000000000000000186Fb1b",
                "0.0.AABBCC22",
                "0.AABBCC22",
                "AABBCC22"
            })
    void nftAllowancesNotFoundException(String accountId) {
        // When
        ThrowingCallable callable =
                () -> restClient.get().uri(callUri, accountId).retrieve().body(NftAllowancesResponse.class);

        // Then
        validateError(
                callable, HttpClientErrorException.NotFound.class, "No account found for the given ID", "Not Found");
    }

    @ParameterizedTest
    @CsvSource({
        "?owner=true&token.id=gt:0.0.1000&limit=1&order=asc,token.id parameter must have account.id present",
        "?owner=true&account.id=gte:0.0.1000&token.id=ne:0.0.1000&limit=1&order=asc,Unsupported range operator ne for token.id",
        "?owner=true&account.id=gte:0.0.1000&account.id=lte:0.0.999&token.id=eq:0.0.1000&limit=1&order=asc,Invalid range provided for account.id",
        "?owner=true&account.id=gte:0.0.1000&token.id=gt:0.0.1000&&token.id=lt:0.0.800&limit=1&order=asc,Invalid range provided for token.id"
    })
    void nftAllowancesRangeValidation(String uriParams, String message) {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(entity.getId()).approvedForAll(true))
                .persist();

        // When
        ThrowingCallable callable = () -> restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        validateError(callable, HttpClientErrorException.BadRequest.class, message, "Bad Request");
    }

    @Test
    void nftAllowancesSecondaryParameterAllowedRange() {
        // Given
        var entity = domainBuilder.entity().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta ->
                        nfta.owner(entity.getId()).tokenId(700).spender(2002).approvedForAll(true))
                .persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta ->
                        nfta.owner(entity.getId()).tokenId(1002).spender(1000).approvedForAll(true))
                .persist();
        var uriParams =
                "?account.id=gte:0.0.1000&account.id=lte:0.0.2002&owner=true&token.id=gt:0.0.1000&&token.id=lt:0.0.800&limit=2&order=asc";
        var next = "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&owner=true&token.id=gt:%s&limit=2&order=asc"
                .formatted(
                        allowance1.getOwner(),
                        EntityId.of(allowance1.getSpender()),
                        EntityId.of(allowance1.getTokenId()));
        // When
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        // Then
        assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2, allowance1), next));
    }

    private NftAllowancesResponse getExpectedResponse(List<NftAllowance> nftAllowances, String next) {
        return new NftAllowancesResponse().allowances(mapper.map(nftAllowances)).links(new Links().next(next));
    }

    private void validateError(
            ThrowableAssert.ThrowingCallable callable,
            Class<? extends HttpClientErrorException> clazz,
            String message,
            String description) {
        assertThatThrownBy(callable)
                .isInstanceOf(clazz)
                .hasMessageContaining(description)
                .asInstanceOf(InstanceOfAssertFactories.type(clazz))
                .extracting(r -> r.getResponseBodyAs(Error.class))
                .extracting(Error::getStatus)
                .extracting(ErrorStatus::getMessages)
                .extracting(List::getFirst)
                .returns(null, ErrorStatusMessagesInner::getData)
                .returns(null, ErrorStatusMessagesInner::getDetail)
                .returns(message, ErrorStatusMessagesInner::getMessage);
    }
}
