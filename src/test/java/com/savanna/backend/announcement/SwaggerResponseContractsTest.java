package com.savanna.backend.announcement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
class SwaggerResponseContractsTest {
    @Autowired WebApplicationContext context;
    JsonNode root;
    JsonNode resolve(JsonNode node){return node.has("$ref") ? root.at(node.get("$ref").asText().substring(1)) : node;}
    JsonNode response(String path,String method,String status){
        var operation=root.get("paths").get(path).get(method);assertNotNull(operation,path+" "+method);
        var response=operation.get("responses").get(status);assertNotNull(response,path+" status "+status);
        assertNotNull(response.get("content"),path+" content");
        var json=response.get("content").get("application/json");assertNotNull(json,path+" JSON media type");
        return resolve(json.get("schema"));
    }
    JsonNode data(String path,String method,String status){return resolve(response(path,method,status).get("properties").get("data"));}
    @Test void responseBodiesDescribeActualFieldsAndMembershipConflict() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        var result=mvc.perform(get("/v3/api-docs")).andReturn().getResponse();assertEquals(200,result.getStatus());
        root=JsonMapper.builder().build().readTree(result.getContentAsString());
        assertTrue(data("/api/v1/teams","post","201").get("properties").has("photoUrl"));
        assertTrue(data("/api/v1/me","get","200").get("properties").has("characterId"));
        var detail=data("/api/v1/teams/{teamId}","get","200");
        assertTrue(detail.get("properties").has("photoUrl"));
        assertTrue(resolve(detail.get("properties").get("members").get("items")).get("properties").has("characterId"));
        for(String path:new String[]{"/api/v1/teams/{teamId}/invitations","/api/v1/invitations/{token}","/api/v1/invitations/{token}/accept"}){
            String method=path.equals("/api/v1/invitations/{token}")?"get":"post";
            String status=path.equals("/api/v1/teams/{teamId}/invitations")?"201":"200";
            assertFalse(data(path,method,status).isMissingNode());assertTrue(data(path,method,status).has("properties"));
        }
        var list=data("/api/v1/teams/{teamId}/invitations","get","200");assertEquals("array",list.get("type").asText());assertTrue(resolve(list.get("items")).has("properties"));
        assertTrue(response("/api/v1/auth/csrf","get","200").get("properties").has("csrfToken"));
        assertTrue(response("/api/v1/auth/logout","post","200").get("properties").has("message"));
        assertTrue(response("/api/v1/teams/{teamId}/invitations/{invitationId}","delete","200").get("properties").has("message"));
        for(String path:new String[]{"/api/v1/teams","/api/v1/invitations/{token}/accept","/api/v1/proposal-recipients/{proposalRecipientId}/response"})
            assertTrue(response(path,path.contains("proposalRecipientId")?"patch":"post","409").get("properties").has("code"));
        assertFalse(root.get("paths").get("/api/v1/invitations/{token}").get("get").has("security"));
        verifyRefs(root);
    }
    void verifyRefs(JsonNode node){
        if(node.isObject()){
            if(node.has("$ref") && node.get("$ref").asText().startsWith("#/"))assertFalse(root.at(node.get("$ref").asText().substring(1)).isMissingNode(),node.get("$ref").asText());
            for(JsonNode child:node)verifyRefs(child);
        }else if(node.isArray())for(JsonNode child:node)verifyRefs(child);
    }
}
