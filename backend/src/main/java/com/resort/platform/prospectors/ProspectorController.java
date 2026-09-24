package com.resort.platform.prospectors;

import com.resort.platform.common.PageResponse;
import com.resort.platform.prospectors.dto.ProspectorResponse;
import com.resort.platform.prospectors.dto.UpdateProspectorRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prospectors")
public class ProspectorController {

    private final ProspectorService prospectorService;

    public ProspectorController(ProspectorService prospectorService) {
        this.prospectorService = prospectorService;
    }

    @GetMapping
    public PageResponse<ProspectorResponse> list(Pageable pageable) {
        return prospectorService.list(pageable);
    }

    @GetMapping("/{id}")
    public ProspectorResponse get(@PathVariable UUID id) {
        return prospectorService.get(id);
    }

    @PutMapping("/{id}")
    public ProspectorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProspectorRequest request) {
        return prospectorService.update(id, request);
    }
}
