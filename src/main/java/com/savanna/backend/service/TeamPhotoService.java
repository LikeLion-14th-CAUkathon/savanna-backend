package com.savanna.backend.service;

import com.savanna.backend.domain.Team;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamPhotoService {
    private final TeamPhotoStorage storage;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private static final long MAX_BYTES=5L*1024*1024;

    public record PhotoResponse(String photoUrl, int expiresInSeconds) {}

    @Transactional
    public PhotoResponse upload(Long userId, Long teamId, MultipartFile file) {
        if(userId==null)throw error(HttpStatus.UNAUTHORIZED,"Login required");
        Team team=teams.findByIdForUpdate(teamId)
                .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Team not found"));
        var member=members.findByUser_UserIdAndTeam_TeamId(userId,teamId)
                .orElseThrow(()->error(HttpStatus.FORBIDDEN,"Team leader required"));
        if(member.getRole()!=TeamRole.LEADER)throw error(HttpStatus.FORBIDDEN,"Team leader required");
        if(file==null || file.isEmpty())throw error(HttpStatus.BAD_REQUEST,"Image file is required");
        if(file.getSize()>MAX_BYTES)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Maximum image size is 5 MiB");
        try {
            byte[] bytes;
            try(var stream=file.getInputStream()) { bytes=stream.readNBytes((int)MAX_BYTES+1); }
            if(bytes.length>MAX_BYTES)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Maximum image size is 5 MiB");
            String format;
            // Read dimensions before decoding to prevent excessive decoded image allocation.
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Only JPEG and PNG are supported");
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if(!format.equals("jpeg") && !format.equals("png"))throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Only JPEG and PNG are supported");
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || width>6000 || height>6000 || (long)width*height>12_000_000)
                        throw error(HttpStatus.BAD_REQUEST,"Maximum dimensions: 6000 per side and 12 megapixels");
                    var image=reader.read(0);
                    // Re-encode: discard EXIF/location metadata and appended non-image content.
                    var output=new ByteArrayOutputStream();
                    if(!ImageIO.write(image,format,output))throw new IOException("Unsupported encoder");
                    bytes=output.toByteArray();
                    if(bytes.length>MAX_BYTES)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Encoded image exceeds 5 MiB");
                } finally { reader.dispose(); }
            }
            String key="team-photos/"+teamId+"/"+UUID.randomUUID()+ (format.equals("jpeg")?".jpg":".png");
            storage.put(key,bytes,format.equals("jpeg")?"image/jpeg":"image/png");
            // Sign before updating the row: AWS failure must not replace the previous image.
            String url=storage.readUrl(key);
            team.updatePhotoObjectKey(key);
            return new PhotoResponse(url,600);
        } catch(ResponseStatusException ex) { throw ex;
        } catch(IOException ex) { throw error(HttpStatus.BAD_REQUEST,"Invalid or unreadable image");
        } catch(software.amazon.awssdk.core.exception.SdkException ex) {
            // Never include AWS response, credentials, or signed URL in client-visible errors.
            throw error(HttpStatus.SERVICE_UNAVAILABLE,"Photo storage temporarily unavailable");
        }
    }
    public String url(Team team) {
        if(team.getPhotoObjectKey()==null) {
            String legacy=team.getPhotoUrl();
            // Never serve a persisted signed URL, embedded data or browser-local preview.
            return legacy!=null && legacy.startsWith("https://") && !legacy.contains("?") ? legacy : null;
        }
        try { return storage.readUrl(team.getPhotoObjectKey()); }
        catch(software.amazon.awssdk.core.exception.SdkException ex) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE,"Photo storage temporarily unavailable");
        }
    }
    public void applyLegacyInput(Team team,String value) {
        if(value==null)return;
        if(!value.isBlank())throw error(HttpStatus.BAD_REQUEST,"Use POST /api/v1/teams/{teamId}/photo to upload a photo; photoUrl is read-only except empty string to remove");
        team.updatePhotoObjectKey(null);
        team.updatePhotoUrl(null);
    }
    private static ResponseStatusException error(HttpStatus status,String message) {
        return new ResponseStatusException(status,message);
    }
}
