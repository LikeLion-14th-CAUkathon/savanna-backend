package com.savanna.backend.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TeamPhotoStorageConfigTest {
    @Test @SuppressWarnings("unchecked")
    void writesPrivateObjectAndPresignsExactKeyForTenMinutes() throws Exception {
        var client=mock(S3Client.class);var signer=mock(S3Presigner.class);
        doAnswer(call->{
            Consumer<PutObjectRequest.Builder> configure=call.getArgument(0);
            var builder=PutObjectRequest.builder();configure.accept(builder);var request=builder.build();
            assertEquals("private-bucket",request.bucket());assertEquals("team-photos/1/a.png",request.key());
            assertEquals("image/png",request.contentType());assertNull(request.acl());
            return null;
        }).when(client).putObject(any(Consumer.class),any(RequestBody.class));
        var signed=mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://example.invalid/temporary").toURL());
        doAnswer(call->{
            Consumer<GetObjectPresignRequest.Builder> configure=call.getArgument(0);
            var builder=GetObjectPresignRequest.builder();configure.accept(builder);var request=builder.build();
            assertEquals(Duration.ofMinutes(10),request.signatureDuration());
            assertEquals("private-bucket",request.getObjectRequest().bucket());
            assertEquals("team-photos/1/a.png",request.getObjectRequest().key());
            return signed;
        }).when(signer).presignGetObject(any(Consumer.class));
        var storage=new TeamPhotoStorageConfig().teamPhotoStorage(client,signer,"private-bucket");
        storage.put("team-photos/1/a.png",new byte[]{1,2,3},"image/png");
        assertEquals("https://example.invalid/temporary",storage.readUrl("team-photos/1/a.png"));
        verify(client).putObject(any(Consumer.class),any(RequestBody.class));
        verify(signer).presignGetObject(any(Consumer.class));
    }
}
