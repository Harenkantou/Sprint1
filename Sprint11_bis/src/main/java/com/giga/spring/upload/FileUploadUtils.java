package com.giga.spring.upload;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Utilitaires pour gérer l'upload de fichiers
 * Sprint 10 - Gestion des uploads
 */
public class FileUploadUtils {
    
    /**
     * Vérifie si la requête contient des fichiers uploadés
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
    
    /**
     * Récupère tous les fichiers uploadés dans une requête
     */
    public static Map<String, UploadedFile> getUploadedFiles(HttpServletRequest request) throws Exception {
        Map<String, UploadedFile> files = new HashMap<>();
        
        if (!isMultipartRequest(request)) {
            return files;
        }
        
        try {
            Collection<Part> parts = request.getParts();
            
            for (Part part : parts) {
                if (part.getSize() > 0 && part.getSubmittedFileName() != null) {
                    // C'est un fichier
                    String fieldName = part.getName();
                    String fileName = part.getSubmittedFileName();
                    String contentType = part.getContentType();
                    long size = part.getSize();
                    
                    // Lire le contenu
                    byte[] content = readPartContent(part);
                    
                    // Créer l'objet UploadedFile
                    UploadedFile uploadedFile = new UploadedFile(fieldName, fileName, contentType, size, content);
                    files.put(fieldName, uploadedFile);
                    
                    System.out.println("File uploaded: " + fileName + " (" + size + " bytes)");
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading uploaded files: " + e.getMessage());
            throw e;
        }
        
        return files;
    }
    
    /**
     * Récupère un fichier spécifique par son nom de champ
     */
    public static UploadedFile getUploadedFile(HttpServletRequest request, String fieldName) throws Exception {
        if (!isMultipartRequest(request)) {
            return null;
        }
        
        try {
            Part part = request.getPart(fieldName);
            if (part != null && part.getSize() > 0 && part.getSubmittedFileName() != null) {
                String fileName = part.getSubmittedFileName();
                String contentType = part.getContentType();
                long size = part.getSize();
                
                byte[] content = readPartContent(part);
                return new UploadedFile(fieldName, fileName, contentType, size, content);
            }
        } catch (Exception e) {
            System.err.println("Error reading uploaded file '" + fieldName + "': " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Récupère tous les fichiers uploadés sous forme de liste
     */
    public static List<UploadedFile> getUploadedFilesList(HttpServletRequest request) throws Exception {
        return new ArrayList<>(getUploadedFiles(request).values());
    }
    
    /**
     * Récupère les fichiers uploadés groupés par champ
     */
    public static Map<String, List<UploadedFile>> getUploadedFilesByField(HttpServletRequest request) throws Exception {
        Map<String, List<UploadedFile>> filesByField = new HashMap<>();
        
        if (!isMultipartRequest(request)) {
            return filesByField;
        }
        
        try {
            Collection<Part> parts = request.getParts();
            
            for (Part part : parts) {
                if (part.getSize() > 0 && part.getSubmittedFileName() != null) {
                    String fieldName = part.getName();
                    String fileName = part.getSubmittedFileName();
                    String contentType = part.getContentType();
                    long size = part.getSize();
                    
                    byte[] content = readPartContent(part);
                    UploadedFile uploadedFile = new UploadedFile(fieldName, fileName, contentType, size, content);
                    
                    filesByField.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(uploadedFile);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading uploaded files: " + e.getMessage());
            throw e;
        }
        
        return filesByField;
    }
    
    /**
     * Lit le contenu d'une Part en bytes
     */
    private static byte[] readPartContent(Part part) throws IOException {
        try (InputStream inputStream = part.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            
            return buffer.toByteArray();
        }
    }
    
    /**
     * Sauvegarde tous les fichiers uploadés dans un répertoire
     */
    public static Map<String, String> saveAllUploadedFiles(HttpServletRequest request, String saveDirectory) throws Exception {
        Map<String, String> savedPaths = new HashMap<>();
        Map<String, UploadedFile> files = getUploadedFiles(request);
        
        for (Map.Entry<String, UploadedFile> entry : files.entrySet()) {
            try {
                String savedPath = entry.getValue().saveTo(saveDirectory);
                savedPaths.put(entry.getKey(), savedPath);
                System.out.println("File saved to: " + savedPath);
            } catch (IOException e) {
                System.err.println("Failed to save file '" + entry.getKey() + "': " + e.getMessage());
                throw e;
            }
        }
        
        return savedPaths;
    }
    
    /**
     * Vérifie la taille totale des fichiers uploadés
     */
    public static boolean validateTotalSize(HttpServletRequest request, long maxTotalSize) throws Exception {
        if (!isMultipartRequest(request)) {
            return true;
        }
        
        long totalSize = 0;
        Collection<Part> parts = request.getParts();
        
        for (Part part : parts) {
            if (part.getSubmittedFileName() != null) {
                totalSize += part.getSize();
                if (totalSize > maxTotalSize) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Vérifie les types de fichiers autorisés
     */
    public static boolean validateFileTypes(HttpServletRequest request, Set<String> allowedTypes) throws Exception {
        if (!isMultipartRequest(request)) {
            return true;
        }
        
        Collection<Part> parts = request.getParts();
        
        for (Part part : parts) {
            if (part.getSubmittedFileName() != null) {
                String contentType = part.getContentType();
                if (contentType != null && !allowedTypes.contains(contentType)) {
                    // Vérifier aussi par extension
                    String fileName = part.getSubmittedFileName().toLowerCase();
                    boolean allowed = false;
                    for (String type : allowedTypes) {
                        if (fileName.endsWith(type.toLowerCase())) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
}