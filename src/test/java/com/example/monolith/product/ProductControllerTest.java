package com.example.monolith.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ACTUATOR_USER=actuator", "ACTUATOR_PASSWORD=pw", "ADMIN_CLIENT_USER=client", "ADMIN_CLIENT_PASSWORD=pw",
        "LOG_FILE_NAME=target/test-logs/monolith.log", "spring.boot.admin.client.auto-registration=false" })
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void crudLifecycle() throws Exception {
        String location = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":49.90}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/products/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Keyboard"));
        mvc.perform(get("/api/products")).andExpect(jsonPath("$[*].name", hasItem("Keyboard")));

        mvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mechanical keyboard\",\"price\":89.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Mechanical keyboard"));

        mvc.perform(delete("/api/products/1")).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$[*].id", not(hasItem(1))));
    }

    @Test
    void rejectsInvalidInputAndUnknownIds() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"price\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/products/999").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"price\":1}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/999")).andExpect(status().isNotFound());
    }
}
