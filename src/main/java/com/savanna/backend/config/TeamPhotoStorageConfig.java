package com.savanna.backend.config;

import com.savanna.backend.service.TeamPhotoStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Configuration
public class TeamPhotoStorageConfig {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="team-photo.enabled",havingValue="true")
    InstanceProfileCredentialsProvider teamPhotoCredentials() {
        // Deliberately excludes environment/static access keys: EC2 instance role only.
        return InstanceProfileCredentialsProvider.create();
    }
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="team-photo.enabled",havingValue="true")
    S3Client teamPhotoS3(InstanceProfileCredentialsProvider credentials,
            @Value("${team-photo.region:ap-northeast-2}") String region) {
        return S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(20)).build()).build();
    }
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="team-photo.enabled",havingValue="true")
    S3Presigner teamPhotoPresigner(InstanceProfileCredentialsProvider credentials,
            @Value("${team-photo.region:ap-northeast-2}") String region) {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials).build();
    }
    @Bean
    @ConditionalOnProperty(name="team-photo.enabled",havingValue="true")
    TeamPhotoStorage teamPhotoStorage(S3Client client, S3Presigner presigner,
            @Value("${team-photo.bucket}") String bucket) {
        if (bucket.isBlank()) throw new IllegalArgumentException("team-photo.bucket is required");
        return new TeamPhotoStorage() {
            public void put(String key, byte[] bytes, String contentType) {
                client.putObject(r -> r.bucket(bucket).key(key).contentType(contentType)
                        .cacheControl("private, max-age=0, no-store"), RequestBody.fromBytes(bytes));
            }
            public String readUrl(String key) {
                return presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(g -> g.bucket(bucket).key(key))).url().toExternalForm();
            }
        };
    }
    @Bean
    @ConditionalOnProperty(name="team-photo.enabled",havingValue="false",matchIfMissing=true)
    TeamPhotoStorage disabledTeamPhotoStorage() {
        return new TeamPhotoStorage() {
            public void put(String key, byte[] bytes, String type) { throw unavailable(); }
            public String readUrl(String key) { throw unavailable(); }
            private org.springframework.web.server.ResponseStatusException unavailable() {
                return new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Team photo storage is disabled");
            }
        };
    }
}
