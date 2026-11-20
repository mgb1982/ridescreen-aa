-- Phone model and installed RAM (public build info), to see which phones the low-memory kills concentrate on.
ALTER TABLE installs ADD COLUMN device TEXT;
ALTER TABLE installs ADD COLUMN ram_mb INTEGER;
