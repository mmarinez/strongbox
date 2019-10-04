package org.carlspring.strongbox.providers.io;

import org.carlspring.commons.encryption.EncryptionAlgorithmsEnum;
import org.carlspring.strongbox.config.Maven2LayoutProviderTestConfig;
import org.carlspring.strongbox.providers.repository.ProxyRepositoryProvider;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.testing.MavenIndexedRepositorySetup;
import org.carlspring.strongbox.testing.repository.MavenRepository;
import org.carlspring.strongbox.testing.storage.repository.RepositoryManagementTestExecutionListener;
import org.carlspring.strongbox.testing.storage.repository.TestRepository.Remote;

import javax.inject.Inject;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import static org.carlspring.strongbox.storage.repository.RepositoryPolicyEnum.SNAPSHOT;
import static org.carlspring.strongbox.util.MessageDigestUtils.calculateChecksum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * @author Przemyslaw Fusik
 * @author Pablo Tirado
 */
@SpringBootTest
@ActiveProfiles({ "MockedRestArtifactResolverTestConfig",
                  "test" })
@ContextConfiguration(classes = Maven2LayoutProviderTestConfig.class)
@Execution(SAME_THREAD)
public class MavenMetadataExpirationProxyCaseTest
        extends BaseMavenMetadataExpirationTest
{

    private static final String REPOSITORY_LOCAL_SOURCE = "mmepc-local-source-repo-snapshots";

    private static final String REPOSITORY_HOSTED = "mmepc-hosted-repo-snapshots";

    private static final String REPOSITORY_PROXY = "mmepc-proxy-repo-snapshots";

    private static final String PROXY_REPOSITORY_URL =
            "http://localhost:48080/storages/" + STORAGE0 + "/" + REPOSITORY_HOSTED + "/";

    @Inject
    private ProxyRepositoryProvider proxyRepositoryProvider;

    @ExtendWith(RepositoryManagementTestExecutionListener.class)
    @Test
    public void expiredProxyRepositoryMetadataPathShouldBeRefetched(@MavenRepository(repositoryId = REPOSITORY_HOSTED,
                                                                                     policy = SNAPSHOT)
                                                                    Repository hostedRepository,
                                                                    @MavenRepository(repositoryId = REPOSITORY_LOCAL_SOURCE,
                                                                                     policy = SNAPSHOT)
                                                                    Repository localSourceRepository,
                                                                    @Remote(url = PROXY_REPOSITORY_URL)
                                                                    @MavenRepository(repositoryId = REPOSITORY_PROXY,
                                                                                     setup = MavenIndexedRepositorySetup.class)
                                                                    Repository proxyRepository)
            throws Exception
    {
        mockHostedRepositoryMetadataUpdate(hostedRepository.getId(),
                                           localSourceRepository.getId(),
                                           versionLevelMetadata,
                                           artifactLevelMetadata);

        mockResolvingProxiedRemoteArtifactsToHostedRepository(REPOSITORY_HOSTED);


        final RepositoryPath hostedPath = resolvePath(hostedRepository.getId(),
                                                      true,
                                                      "maven-metadata.xml");
        String sha1HostedPathChecksum = readChecksum(resolveSiblingChecksum(hostedPath,
                                                                            EncryptionAlgorithmsEnum.SHA1));
        assertThat(sha1HostedPathChecksum).isNotNull();

        final RepositoryPath proxiedPath = resolvePath(proxyRepository.getId(),
                                                       true,
                                                       "maven-metadata.xml");
        String sha1ProxiedPathChecksum = readChecksum(resolveSiblingChecksum(proxiedPath,
                                                                             EncryptionAlgorithmsEnum.SHA1));
        assertThat(sha1ProxiedPathChecksum).isNull();

        assertThat(RepositoryFiles.artifactExists(proxiedPath)).isFalse();

        proxyRepositoryProvider.fetchPath(proxiedPath);
        assertThat(RepositoryFiles.artifactExists(proxiedPath)).isTrue();

        sha1ProxiedPathChecksum = readChecksum(resolveSiblingChecksum(proxiedPath,
                                                                      EncryptionAlgorithmsEnum.SHA1));
        assertThat(sha1ProxiedPathChecksum).isNotNull();
        assertThat(sha1HostedPathChecksum).isEqualTo(sha1ProxiedPathChecksum);

        String calculatedProxiedPathChecksum = calculateChecksum(proxiedPath,
                                                                 EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertThat(calculatedProxiedPathChecksum).isEqualTo(sha1ProxiedPathChecksum);

        Files.setLastModifiedTime(proxiedPath, oneHourAgo());

        proxyRepositoryProvider.fetchPath(proxiedPath);
        sha1ProxiedPathChecksum = readChecksum(resolveSiblingChecksum(proxiedPath,
                                                                      EncryptionAlgorithmsEnum.SHA1));
        assertThat(calculatedProxiedPathChecksum).isEqualTo(sha1ProxiedPathChecksum);

        mockHostedRepositoryMetadataUpdate(hostedRepository.getId(),
                                           localSourceRepository.getId(),
                                           versionLevelMetadata,
                                           artifactLevelMetadata);

        sha1HostedPathChecksum = readChecksum(resolveSiblingChecksum(hostedPath,
                                                                     EncryptionAlgorithmsEnum.SHA1));
        final String calculatedHostedPathChecksum = calculateChecksum(hostedPath,
                                                                      EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertThat(calculatedHostedPathChecksum).isEqualTo(sha1HostedPathChecksum);
        assertThat(sha1ProxiedPathChecksum).isNotEqualTo(calculatedHostedPathChecksum);

        Files.setLastModifiedTime(proxiedPath, oneHourAgo());

        proxyRepositoryProvider.fetchPath(proxiedPath);
        sha1ProxiedPathChecksum = readChecksum(resolveSiblingChecksum(proxiedPath,
                                                                      EncryptionAlgorithmsEnum.SHA1));
        assertThat(calculatedHostedPathChecksum).isEqualTo(sha1ProxiedPathChecksum);
        calculatedProxiedPathChecksum = calculateChecksum(proxiedPath,
                                                          EncryptionAlgorithmsEnum.SHA1.getAlgorithm());
        assertThat(calculatedProxiedPathChecksum).isEqualTo(sha1ProxiedPathChecksum);
    }

    @Override
    protected String getRepositoryHostedId()
    {
        return REPOSITORY_HOSTED;
    }

    @Override
    protected String getRepositoryProxyId()
    {
        return REPOSITORY_PROXY;
    }

}
