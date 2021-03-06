/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

public class CliqueMinerExecutorTest {

  private static final GenesisConfigOptions GENESIS_CONFIG_OPTIONS =
      GenesisConfigOptions.fromGenesisConfig(new JsonObject());
  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddress;
  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    validatorList.add(localAddress);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 1));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 2));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 3));

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();

    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer, null);
    cliqueProtocolContext = new ProtocolContext<>(null, null, cliqueContext);
    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void extraDataCreatedOnEpochBlocksContainsValidators() {
    final byte[] vanityData = new byte[32];
    new Random().nextBytes(vanityData);
    final BytesValue wrappedVanityData = BytesValue.wrap(vanityData);
    final int EPOCH_LENGTH = 10;

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            Executors.newSingleThreadExecutor(),
            CliqueProtocolSchedule.create(GENESIS_CONFIG_OPTIONS, proposerKeyPair),
            new PendingTransactions(1),
            proposerKeyPair,
            new MiningParameters(AddressHelpers.ofValue(1), Wei.ZERO, wrappedVanityData, false),
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH));

    // NOTE: Passing in the *parent* block, so must be 1 less than EPOCH
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH - 1).buildHeader();

    final BytesValue extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(extraDataBytes);

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(wrappedVanityData);
    assertThat(cliqueExtraData.getValidators())
        .containsExactly(validatorList.toArray(new Address[0]));
  }

  @Test
  public void extraDataForNonEpochBlocksDoesNotContainValidaors() {
    final byte[] vanityData = new byte[32];
    new Random().nextBytes(vanityData);
    final BytesValue wrappedVanityData = BytesValue.wrap(vanityData);
    final int EPOCH_LENGTH = 10;

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            Executors.newSingleThreadExecutor(),
            CliqueProtocolSchedule.create(GENESIS_CONFIG_OPTIONS, proposerKeyPair),
            new PendingTransactions(1),
            proposerKeyPair,
            new MiningParameters(AddressHelpers.ofValue(1), Wei.ZERO, wrappedVanityData, false),
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH));

    // Parent block was epoch, so the next block should contain no validators.
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH).buildHeader();

    final BytesValue extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(extraDataBytes);

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(wrappedVanityData);
    assertThat(cliqueExtraData.getValidators()).isEqualTo(Lists.newArrayList());
  }
}
