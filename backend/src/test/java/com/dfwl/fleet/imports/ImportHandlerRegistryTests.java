package com.dfwl.fleet.imports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.dto.response.ImportRowResponse;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.service.ImportRowCommitService;
import com.dfwl.fleet.imports.spi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImportHandlerRegistryTests {
    private final ImportRepository repository=mock(ImportRepository.class);
    private final ObjectMapper mapper=new ObjectMapper();

    private List<ImportBusinessHandler> handlers() {
        var handlers=new ArrayList<ImportBusinessHandler>();
        for(var type:ImportBusinessType.values()){
            var handler=mock(ImportBusinessHandler.class);when(handler.businessType()).thenReturn(type);
            when(handler.resolveBusinessKey(any())).thenReturn(new ImportValidationResult(true,"KEY",null,null));
            when(handler.validateDuplicate(any())).thenReturn(new ImportValidationResult(true,"KEY",null,null));
            when(handler.validate(any())).thenReturn(new ImportValidationResult(true,"KEY",null,null));
            when(handler.commit(any())).thenReturn(new ImportCommitResult(ImportCommitResult.Status.SUCCESS,1L,"KEY",null,null));
            handlers.add(handler);
        }
        return handlers;
    }

    @ParameterizedTest @EnumSource(ImportBusinessType.class)
    void everyMissingHandlerFailsExplicitly(ImportBusinessType type) {
        var handlers=handlers();handlers.removeIf(h->h.businessType()==type);
        assertThatThrownBy(()->new ImportRowCommitService(repository,mapper,handlers))
            .isInstanceOf(IllegalStateException.class).hasMessage("Missing import handler: "+type);
    }

    @ParameterizedTest @EnumSource(ImportBusinessType.class)
    void everyDuplicateHandlerFailsExplicitly(ImportBusinessType type) {
        var handlers=handlers();handlers.add(handlers.stream().filter(h->h.businessType()==type).findFirst().orElseThrow());
        assertThatThrownBy(()->new ImportRowCommitService(repository,mapper,handlers))
            .isInstanceOf(IllegalStateException.class).hasMessage("Duplicate import handler: "+type);
    }

    @ParameterizedTest @EnumSource(ImportBusinessType.class)
    void registrationOrderDoesNotChangePreviewOrCommitDispatch(ImportBusinessType type) {
        var handlers=handlers();
        for(int order=0;order<2;order++){
            Collections.reverse(handlers);
            var service=new ImportRowCommitService(repository,mapper,handlers);
            handlers.forEach(h->clearInvocations(h));
            var preview=service.preparePreview(type.name(),new ImportPreviewRequest.ImportRowRequest(1,Map.of(),null),new HashSet<>());
            assertThat(preview.validation().success()).isTrue();
            var row=mock(ImportRowResponse.class);when(row.id()).thenReturn(10L);when(row.rawDataJson()).thenReturn("{}");
            assertThat(service.commitRow(type.name(),row,new HashSet<>(),7).finalStatus()).isEqualTo("SUCCESS");
            for(var handler:handlers){
                if(handler.businessType()==type){verify(handler,times(2)).resolveBusinessKey(any());verify(handler,times(2)).validateDuplicate(any());verify(handler).validate(any());verify(handler).commit(any());}
                else {verify(handler,never()).resolveBusinessKey(any());verify(handler,never()).commit(any());}
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"blank","explicit","batch","history","nullType","nullTypeBlank"})
    void unsupportedTypeRetainsExistingErrorPriority(String scenario) {
        String type=scenario.startsWith("nullType")?null:"UNKNOWN";
        String explicit=Set.of("blank","nullTypeBlank").contains(scenario)?null:" KEY ";
        String code=switch(scenario){case "blank" -> "IMPORT_KEY_MISSING";case "batch" -> "IMPORT_DUPLICATE_IN_BATCH";case "history" -> "IMPORT_DUPLICATE_EXISTING";case "nullType","nullTypeBlank" -> "IMPORT_FORMAT_ERROR";default -> "IMPORT_UNSUPPORTED_TYPE";};
        String message=switch(code){case "IMPORT_KEY_MISSING" -> "业务唯一键不能为空";case "IMPORT_DUPLICATE_IN_BATCH" -> "导入批次内重复";case "IMPORT_DUPLICATE_EXISTING" -> "业务数据已存在";case "IMPORT_FORMAT_ERROR" -> "导入行格式错误";default -> "不支持的导入类型";};
        if(scenario.equals("history"))when(repository.committedKeyExists("UNKNOWN","KEY")).thenReturn(true);
        var service=new ImportRowCommitService(repository,mapper,handlers());
        var keys=new HashSet<String>();if(scenario.equals("batch"))keys.add("KEY");
        var preview=service.preparePreview(type,new ImportPreviewRequest.ImportRowRequest(1,Map.of(),explicit),keys);
        assertThat(preview.validation().errorCode()).isEqualTo(code);assertThat(preview.validation().errorMessage()).isEqualTo(message);
        assertThat(preview.businessUniqueKey()).isEqualTo(scenario.startsWith("nullType")?explicit:Set.of("blank","explicit").contains(scenario)?null:"KEY");
        keys=new HashSet<>();if(scenario.equals("batch"))keys.add("KEY");
        var row=mock(ImportRowResponse.class);when(row.id()).thenReturn(10L);when(row.rawDataJson()).thenReturn("{}");when(row.businessUniqueKey()).thenReturn(explicit);
        assertThat(service.commitRow(type,row,keys,7).finalStatus()).isEqualTo("FAILURE");
        verify(repository).markRowFinal(10L,"FAILURE",code,message,type,null,scenario.startsWith("nullType")?explicit:scenario.equals("blank")?null:"KEY");
    }
}
