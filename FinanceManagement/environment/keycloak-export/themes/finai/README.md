# FinAI Keycloak Theme

A modern, dark-themed Keycloak authentication interface inspired by the Finance AI Hub design with glassmorphism effects and electric purple accents.

## Features

- ✨ Modern dark UI with glassmorphism effects
- 🎨 Electric purple accent color (#a78bfa)
- 📱 Fully responsive design
- 🌐 Multi-language support (English & Vietnamese)
- 🔐 Custom login, registration, password reset pages
- 🎭 Split-screen design with AI-themed background

## Installation

The theme is already mounted in your Docker Compose configuration at:
```
./keycloak-export/themes/finai
```

## Activation

### Method 1: Keycloak Admin Console (Recommended)

1. Start Keycloak:
   ```bash
   cd environment
   docker compose up -d keycloak
   ```

2. Open Keycloak Admin Console: http://localhost:7080
   - Username: `admin`
   - Password: `admin`

3. Select your realm (`finance`)

4. Go to **Realm Settings** > **Themes** tab

5. Set the following:
   - **Login Theme**: `finai`
   - **Account Theme**: `finai` (optional, falls back to keycloak)
   - **Email Theme**: `finai` (optional)

6. Click **Save**

7. Test by logging out and visiting the login page

### Method 2: Realm Configuration File

Edit your realm JSON configuration file and set:

```json
{
  "loginTheme": "finai",
  "accountTheme": "finai",
  "emailTheme": "finai"
}
```

Then restart Keycloak.

## Theme Structure

```
finai/
├── theme.properties                 # Global theme config
├── login/                           # Login theme
│   ├── theme.properties            # Login-specific config
│   ├── template.ftl                # Base template with layout
│   ├── login.ftl                   # Login page
│   ├── register.ftl                # Registration page
│   ├── login-reset-password.ftl    # Password reset page
│   ├── login-verify-email.ftl      # Email verification page
│   ├── error.ftl                   # Error page
│   ├── messages/                   # Translations
│   │   ├── messages_en.properties  # English
│   │   └── messages_vi.properties  # Vietnamese
│   └── resources/
│       ├── css/
│       │   └── finai.css           # Main stylesheet
│       └── img/
│           └── ai-finance-bg.png   # Background image
```

## Customization

### Colors

Edit `/login/resources/css/finai.css` and modify the CSS variables:

```css
:root {
  --background: hsl(222, 47%, 11%);     /* Deep Navy */
  --primary: hsl(252, 87%, 67%);         /* Electric Purple */
  --secondary: hsl(217, 33%, 17%);       /* Dark Gray */
  /* ... more colors */
}
```

### Background Image

Replace `/login/resources/img/ai-finance-bg.png` with your custom image.

The image should be:
- High resolution (1920x1080 or higher)
- Dark-themed to match the UI
- Optimized for web (compressed)

### Typography

The theme uses:
- **Inter** for body text
- **Outfit** for headings

To change fonts, edit the CSS file:

```css
@import url('https://fonts.googleapis.com/css2?family=YourFont&display=swap');

body {
  font-family: 'YourFont', sans-serif;
}
```

### Translations

Add or modify translations in:
- `/login/messages/messages_en.properties` (English)
- `/login/messages/messages_vi.properties` (Vietnamese)

Format:
```properties
key=Translation text
loginTitle=Sign in to FinAI
```

## Development Mode

Keycloak is already running in development mode (`start-dev`), which means:
- ✅ Theme changes are applied immediately (no restart needed)
- ✅ No caching of theme resources
- ✅ Easier debugging

Just edit the files and refresh your browser!

## Troubleshooting

### Theme not showing

1. Check Keycloak logs:
   ```bash
   docker compose logs -f keycloak
   ```

2. Verify theme is mounted:
   ```bash
   docker exec -it keycloak ls -la /opt/keycloak/themes/finai
   ```

3. Clear browser cache (Ctrl+Shift+R)

### CSS not loading

1. Check the CSS file path in `theme.properties`
2. Verify file permissions:
   ```bash
   ls -la environment/keycloak-export/themes/finai/login/resources/css/
   ```

### Background image not showing

1. Verify image exists:
   ```bash
   ls -la environment/keycloak-export/themes/finai/login/resources/img/
   ```

2. Check browser console for 404 errors

3. The right panel with background is hidden on mobile (<1024px width)

## Design Credits

Based on the Finance AI Hub design with:
- Modern glassmorphism effects
- Dark navy background (#1a1f37)
- Electric purple primary color (#a78bfa)
- AI-themed floating cards
- Split-screen layout

## Browser Support

- ✅ Chrome/Edge (latest)
- ✅ Firefox (latest)
- ✅ Safari (latest)
- ✅ Mobile browsers

## License

This theme is part of the Finance Management project.
