package com.giga.spring.upload;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Classe représentant un fichier uploadé
 * Sprint 10 - Support upload de fichiers
 */
public class UploadedFile {
    private String fieldName;      // Nom du champ dans le formulaire
    private String fileName;       // Nom original du fichier
    private String contentType;    // Type MIME
    private long size;             // Taille en bytes
    private byte[] content;        // Contenu du fichier
    private String tempFilePath;   // Chemin temporaire (si sauvegardé)
    
    // Constructeurs
    public UploadedFile() {}
    
    public UploadedFile(String fieldName, String fileName, String contentType, 
                       long size, byte[] content) {
        this.fieldName = fieldName;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.content = content;
    }
    
    // Getters et Setters
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    
    public String getTempFilePath() { return tempFilePath; }
    public void setTempFilePath(String tempFilePath) { this.tempFilePath = tempFilePath; }
    
    /**
     * Sauvegarde le fichier dans un répertoire temporaire
     */
    public String saveToTemp() throws IOException {
        if (content == null || content.length == 0) {
            throw new IOException("No content to save");
        }
        
        // Créer un nom de fichier temporaire unique
        String tempDir = System.getProperty("java.io.tmpdir");
        String prefix = "upload_" + System.currentTimeMillis() + "_";
        Path tempFile = Files.createTempFile(prefix, getFileExtension());
        
        // Écrire le contenu
        Files.write(tempFile, content);
        
        this.tempFilePath = tempFile.toString();
        return tempFilePath;
    }
    
    /**
     * Sauvegarde le fichier dans un répertoire spécifique
     */
    public String saveTo(String directory) throws IOException {
        if (content == null || content.length == 0) {
            throw new IOException("No content to save");
        }
        
        // S'assurer que le répertoire existe
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        // Créer le chemin du fichier
        String safeFileName = makeSafeFileName(fileName);
        Path filePath = dirPath.resolve(safeFileName);
        
        // Si le fichier existe déjà, ajouter un suffixe
        int counter = 1;
        while (Files.exists(filePath)) {
            String nameWithoutExt = safeFileName.replaceFirst("[.][^.]+$", "");
            String extension = safeFileName.substring(nameWithoutExt.length());
            safeFileName = nameWithoutExt + "_" + counter + extension;
            filePath = dirPath.resolve(safeFileName);
            counter++;
        }
        
        // Écrire le contenu
        Files.write(filePath, content);
        
        this.tempFilePath = filePath.toString();
        return tempFilePath;
    }
    
    /**
     * Sauvegarde le fichier avec un nom spécifique
     */
    public String saveAs(String filePath) throws IOException {
        if (content == null || content.length == 0) {
            throw new IOException("No content to save");
        }
        
        Path path = Paths.get(filePath);
        
        // Créer les répertoires parents si nécessaire
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        
        // Écrire le contenu
        Files.write(path, content);
        
        this.tempFilePath = path.toString();
        return tempFilePath;
    }
    
    /**
     * Lit le contenu du fichier comme une chaîne de caractères
     */
    public String getContentAsString() {
        return new String(content);
    }
    
    /**
     * Lit le contenu du fichier comme une chaîne de caractères avec un encodage spécifique
     */
    public String getContentAsString(String charsetName) throws UnsupportedEncodingException {
        return new String(content, charsetName);
    }
    
    /**
     * Récupère l'extension du fichier
     */
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return ".dat";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
    
    /**
     * Supprime le fichier temporaire
     */
    public void deleteTempFile() throws IOException {
        if (tempFilePath != null && !tempFilePath.isEmpty()) {
            Files.deleteIfExists(Paths.get(tempFilePath));
            tempFilePath = null;
        }
    }
    
    /**
     * Vérifie si le fichier est une image
     */
    public boolean isImage() {
        if (contentType == null) return false;
        return contentType.startsWith("image/");
    }
    
    /**
     * Vérifie si le fichier est un PDF
     */
    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }
    
    /**
     * Vérifie si le fichier est un document texte
     */
    public boolean isText() {
        if (contentType == null) return false;
        return contentType.startsWith("text/") || 
               contentType.contains("javascript") ||
               contentType.contains("json") ||
               contentType.contains("xml");
    }
    
    /**
     * Nettoie le nom de fichier pour la sécurité
     */
    private String makeSafeFileName(String fileName) {
        if (fileName == null) return "uploaded_file.dat";
        
        // Remplacer les caractères dangereux
        String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // Limiter la longueur
        if (safeName.length() > 255) {
            int dotIndex = safeName.lastIndexOf('.');
            if (dotIndex > 0) {
                String name = safeName.substring(0, Math.min(200, dotIndex));
                String ext = safeName.substring(dotIndex);
                safeName = name + ext;
            } else {
                safeName = safeName.substring(0, 255);
            }
        }
        
        return safeName;
    }
    
    @Override
    public String toString() {
        return "UploadedFile{" +
               "fieldName='" + fieldName + '\'' +
               ", fileName='" + fileName + '\'' +
               ", contentType='" + contentType + '\'' +
               ", size=" + size +
               ", hasContent=" + (content != null) +
               ", tempFilePath='" + tempFilePath + '\'' +
               '}';
    }
}