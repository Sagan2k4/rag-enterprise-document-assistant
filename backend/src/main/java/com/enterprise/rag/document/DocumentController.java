package com.enterprise.rag.document;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.security.AuthUser;
import com.enterprise.rag.security.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal AuthUser user) {
        return documentService.listVisible(user).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return DocumentResponse.from(documentService.getVisible(id, user));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("allowedRoles") Set<Role> allowedRoles,
            @AuthenticationPrincipal AuthUser user) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw ApiException.badRequest("File name is required");
        }
        KnowledgeDocument doc = documentService.register(file.getBytes(), filename, title,
                SourceType.fromFilename(filename), null, allowedRoles, user.username());
        return DocumentResponse.from(doc);
    }

    public record WikiImportRequest(@NotBlank String url, String title, @NotEmpty Set<Role> allowedRoles) {
    }

    @PostMapping("/wiki")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse importWiki(@Valid @RequestBody WikiImportRequest request,
            @AuthenticationPrincipal AuthUser user) {
        return DocumentResponse.from(documentService.registerWikiPage(request.url(), request.title(),
                request.allowedRoles(), user.username()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }
}
