/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.contractlog;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

public class ApproveForAllAllowanceContractLog extends AbstractSyntheticContractLog {
    public ApproveForAllAllowanceContractLog(
            RecordItem recordItem, EntityId tokenId, EntityId ownerId, EntityId spenderId, boolean approved) {
        super(
                recordItem,
                tokenId,
                APPROVE_FOR_ALL_SIGNATURE,
                entityIdToBytes(ownerId),
                entityIdToBytes(spenderId),
                null,
                booleanToBytes(approved));
    }
}
