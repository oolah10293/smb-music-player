package org.oolah.s24backup;

public final class RemotePaths {
    private RemotePaths() { }

    // Reject traversal and illegal SMB path elements rather than silently changing destinations.
    public static String safe(String raw) {
        if (raw == null) throw new IllegalArgumentException("Folder name cannot be null.");
        String value = raw.trim().replace('/', '\\');
        String[] parts = value.split("\\\\", -1);
        StringBuilder b = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.equals(".") || part.equals("..")
                    || part.matches(".*[<>:\"|?*].*")
                    || part.endsWith(".") || part.endsWith(" "))
                throw new IllegalArgumentException("Invalid folder name: " + part);
            if (b.length() > 0) b.append('\\');
            b.append(part);
        }
        return b.toString();
    }

    public static String join(String first, String second) {
        String a = safe(first), b = safe(second);
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "\\" + b;
    }
}
