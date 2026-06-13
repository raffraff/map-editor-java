package com.rose.mapeditor;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.rose.mapeditor.screen.EditorScreen;

/** Root libGDX application. */
public class MapEditorGame extends Game {

    private String gameRootPath;

    public MapEditorGame(String gameRootPath) {
        this.gameRootPath = gameRootPath;
    }

    @Override
    public void create() {
        if (gameRootPath != null && !gameRootPath.isEmpty()) {
            GameData.get().setGameRoot(gameRootPath);
        }

        setScreen(new EditorScreen());
    }

    public void setGameRoot(String path) {
        this.gameRootPath = path;
        GameData.get().setGameRoot(path);
    }

    public String getGameRootPath() {
        return gameRootPath;
    }

    /** Opens native folder picker on desktop (via absolute path env var fallback). */
    public static String defaultGameRoot() {
        String env = System.getenv("ROSE_GAME_ROOT");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        FileHandle userDir = Gdx.files.absolute(System.getProperty("user.dir"));
        return userDir.path();
    }
}
