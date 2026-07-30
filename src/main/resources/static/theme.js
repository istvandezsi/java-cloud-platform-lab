(() => {
    const storageKey = "cloudlab-theme";
    const systemPreference = "system";
    const supportedPreferences = new Set(["light", "dark", systemPreference]);
    const systemTheme = window.matchMedia("(prefers-color-scheme: dark)");

    function readPreference() {
        try {
            const storedPreference = window.localStorage.getItem(storageKey);
            return supportedPreferences.has(storedPreference) ? storedPreference : systemPreference;
        } catch {
            return systemPreference;
        }
    }

    function resolveTheme(preference) {
        if (preference !== systemPreference) {
            return preference;
        }

        return systemTheme.matches ? "dark" : "light";
    }

    function applyPreference(preference) {
        const normalizedPreference = supportedPreferences.has(preference)
            ? preference
            : systemPreference;

        document.documentElement.dataset.themePreference = normalizedPreference;
        document.documentElement.dataset.theme = resolveTheme(normalizedPreference);
    }

    function persistPreference(preference) {
        try {
            if (preference === systemPreference) {
                window.localStorage.removeItem(storageKey);
            } else {
                window.localStorage.setItem(storageKey, preference);
            }
        } catch {
            // The selected theme still applies for the current page when storage is unavailable.
        }
    }

    function setPreference(preference) {
        if (!supportedPreferences.has(preference)) {
            return;
        }

        applyPreference(preference);
        persistPreference(preference);
    }

    function getPreference() {
        return document.documentElement.dataset.themePreference || systemPreference;
    }

    systemTheme.addEventListener("change", () => {
        if (getPreference() === systemPreference) {
            applyPreference(systemPreference);
        }
    });

    window.addEventListener("storage", (event) => {
        if (event.key === storageKey) {
            applyPreference(readPreference());
        }
    });

    applyPreference(readPreference());

    window.cloudLabTheme = Object.freeze({
        getPreference,
        setPreference
    });
})();
