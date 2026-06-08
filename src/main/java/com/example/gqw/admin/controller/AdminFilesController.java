package com.example.gqw.admin.controller;

import com.example.gqw.shop.service.LibraryStorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminFilesController {

    private final LibraryStorageService libraryStorageService;
    private final AdminControllerSupport controllerSupport;

    public AdminFilesController(
        LibraryStorageService libraryStorageService,
        AdminControllerSupport controllerSupport
    ) {
        this.libraryStorageService = libraryStorageService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/files")
    public String files(
        @RequestParam(required = false) String folder,
        Model model
    ) {
        AdminControllerSupport.LibraryViewData libraryViewData = controllerSupport.prepareLibraryViewData(folder);
        model.addAttribute("selectedFolder", libraryViewData.selectedFolder());
        model.addAttribute("fileFolders", libraryViewData.folders());
        model.addAttribute("libraryFiles", libraryViewData.files());
        return "admin/files";
    }

    @PostMapping("/admin/files/upload")
    public String uploadFiles(
        @RequestParam(required = false) List<MultipartFile> files,
        @RequestParam(required = false, defaultValue = "library") String folder,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        String selectedFolder;
        try {
            selectedFolder = libraryStorageService.normalizeFolderKey(folder);
            var stored = libraryStorageService.store(selectedFolder, files);
            redirectAttributes.addFlashAttribute("filesSuccess", "Загружено файлов: " + stored.size());
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            selectedFolder = "library";
            redirectAttributes.addFlashAttribute("filesError", ex.getMessage());
        }
        redirectAttributes.addAttribute("folder", selectedFolder);
        return "redirect:/admin/files";
    }

    @GetMapping("/admin/files/list")
    @ResponseBody
    public Map<String, Object> filesListApi(@RequestParam(required = false, defaultValue = "library") String folder) {
        String selectedFolder;
        try {
            selectedFolder = libraryStorageService.normalizeFolderKey(folder);
        } catch (IllegalArgumentException ex) {
            selectedFolder = "library";
        }
        return Map.of(
            "ok", true,
            "selectedFolder", selectedFolder,
            "folders", libraryStorageService.listFolders(),
            "files", libraryStorageService.listFilesInFolder(selectedFolder)
        );
    }

    @PostMapping("/admin/files/upload-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFilesJson(
        @RequestParam(required = false) List<MultipartFile> files,
        @RequestParam(required = false, defaultValue = "library") String folder
    ) {
        String selectedFolder;
        try {
            selectedFolder = libraryStorageService.normalizeFolderKey(folder);
            var stored = libraryStorageService.store(selectedFolder, files);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "uploadedCount", stored.size(),
                "selectedFolder", selectedFolder,
                "folders", libraryStorageService.listFolders(),
                "files", libraryStorageService.listFilesInFolder(selectedFolder)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/admin/files/delete-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFileJson(
        @RequestParam String url,
        @RequestParam(required = false, defaultValue = "library") String folder
    ) {
        try {
            libraryStorageService.deleteByUrl(url);
            String selectedFolder = libraryStorageService.normalizeFolderKey(folder);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "selectedFolder", selectedFolder,
                "folders", libraryStorageService.listFolders(),
                "files", libraryStorageService.listFilesInFolder(selectedFolder)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "message", ex.getMessage()
            ));
        }
    }
}
