from pathlib import Path

lib = Path('app/src/main/java/dev/behradhz/meowzix/feature/library/LibraryRoute.kt')
text = lib.read_text()
old = '''                        onPinOffline = onPinOffline,
                        availability = state.availability,
                        playlists = state.playlists,
                        onFavorite = onFavorite,
'''
new = '''                        onPinOffline = onPinOffline,
                        availability = state.availability,
                        downloads = state.downloads,
                        playlists = state.playlists,
                        onFavorite = onFavorite,
'''
if old not in text:
    raise SystemExit('Favorites TracksSection call not found')
lib.write_text(text.replace(old, new, 1))

app = Path('app/src/main/java/dev/behradhz/meowzix/navigation/MeowzixApp.kt')
text = app.read_text()
if 'import androidx.compose.runtime.LaunchedEffect\n' not in text:
    marker = 'import androidx.compose.runtime.Composable\n'
    if marker not in text:
        raise SystemExit('Composable import marker not found')
    text = text.replace(marker, marker + 'import androidx.compose.runtime.LaunchedEffect\n', 1)
app.write_text(text)
