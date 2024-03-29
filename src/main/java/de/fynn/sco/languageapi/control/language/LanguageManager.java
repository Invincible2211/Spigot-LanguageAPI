package de.fynn.sco.languageapi.control.language;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import de.fynn.sco.languageapi.LanguageAPIPlugin;
import de.fynn.sco.languageapi.control.database.DatabaseConnector;
import de.fynn.sco.languageapi.control.file.ConfigurationLoader;
import de.fynn.sco.languageapi.model.file.LanguageFile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * @author Freddyblitz
 * @version 1.0
 */
public class LanguageManager {

    /*----------------------------------------------ATTRIBUTE---------------------------------------------------------*/

    private final HashMap<UUID,String> playerLanguageHashMap = new HashMap<>();

    private final HashMap<UUID, Boolean> playerAutoChangeLanguage = new HashMap<>();

    private HashMap<Plugin, HashMap<String,LanguageFile>> languageFiles = new HashMap<>();

    private final List<String> availableLanguage = new ArrayList<>();

    private String defaultLanguage;

    private final DatabaseConnector databaseConnector = DatabaseConnector.getInstance();

    private static LanguageManager instance = new LanguageManager();

    private final HashMap<String, String> languageNameMapping = new HashMap<>();

    /*--------------------------------------------KONSTRUKTOREN-------------------------------------------------------*/

    /**
     * Der Konstruktor des LanguageManagers setzt die Instanzvariable auf die aktuelle Instanz,
     * die durch diesen Konstruktor erzeugt wird und fuehrt dann eine {@link #init() Initialisierung} durch.
     * Ausserdem wird ein Listener bei ProtocolLib regestriert, der Packages bekommt die beim Beitreten eines Spielers
     * auf dem Server oder beim Wechseln der Client-Einstellungen gesendet wird. Dies dient zur automatischen
     * Spracherkennung des Spielers.
     */
    private LanguageManager(){
        this.init();

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener(){

            public Plugin getPlugin() {
                return LanguageAPIPlugin.getPlugin();
            }

            public ListeningWhitelist getReceivingWhitelist() {
                return ListeningWhitelist.newBuilder().gamePhase(GamePhase.PLAYING).highest().types(PacketType.Play.Client.SETTINGS).build();
            }

            public ListeningWhitelist getSendingWhitelist() {
                return ListeningWhitelist.newBuilder().gamePhase(GamePhase.PLAYING).highest().types(PacketType.Play.Client.SETTINGS).build();
            }

            @Override
            public void onPacketReceiving(PacketEvent e) {
                if (!(e.getPacket().getType() == PacketType.Play.Client.SETTINGS)) return;
                String language = e.getPacket().getStrings().readSafely(0).toLowerCase(Locale.ROOT);
                if (playerAutoChangeLanguage.get(e.getPlayer().getUniqueId())){
                    setLanguage(e.getPlayer().getUniqueId(), language);
                }
            }

            public void onPacketSending(PacketEvent e) {
                return;
            }

        });
    }

    /*----------------------------------------------METHODEN----------------------------------------------------------*/

    /**
     * Hiermit wird ein neuer Spieler im System registriert.
     * @param player Der zu registrierende Spieler
     */
    public void registerPlayer(Player player){
        UUID uuid = player.getUniqueId();
        if (this.databaseConnector.alreadyExists(uuid)){
            this.playerLanguageHashMap.put(uuid, this.databaseConnector.loadPlayer(uuid));
            this.playerAutoChangeLanguage.put(uuid, this.databaseConnector.hasAutoUpdate(uuid));
        } else {
            this.playerLanguageHashMap.put(uuid, player.getLocale());
            this.playerAutoChangeLanguage.put(uuid, true);
            this.databaseConnector.insertPlayer(uuid, player.getLocale());
        }
    }

    /**
     * Damit kann ein neues Plugin zur verwaltung von dessen Sprachen registriert werden.
     * @param plugin Das zu registrierende Plugin
     * @param defaultLanguage Die Sprachdatei, die, wenn nicht anders angegeben oder weil es nicht anders möglich ist,
     *                       verwendet werden soll
     */
    public void registerPlugin(Plugin plugin, LanguageFile defaultLanguage){
        HashMap<String, LanguageFile> pluginLanguageFileHashMap = new HashMap<>();
        pluginLanguageFileHashMap.put(defaultLanguage.getIdentifier(), defaultLanguage);
        this.languageFiles.put(plugin,pluginLanguageFileHashMap);
        if (!this.containsLanguage(defaultLanguage.getIdentifier())){
            this.availableLanguage.add(defaultLanguage.getIdentifier());
            this.languageNameMapping.put(defaultLanguage.getIdentifier(), defaultLanguage.getName());
        }
    }

    /**
     * Damit kann eine neue Uebersetzung fuer ein Plugin hinzugefuegt werden.
     * @param plugin Das Plugin, welches eine weitere Uebersetzung erhaelt
     * @param languageFile Die Sprachdatei, die die Uebersetzungen enthaelt
     */
    public void registerLanguage(Plugin plugin, LanguageFile languageFile){
        HashMap<String, LanguageFile> pluginLanguageFileHashMap = this.languageFiles.get(plugin);
        pluginLanguageFileHashMap.put(languageFile.getIdentifier(), languageFile);
        if (!this.containsLanguage(languageFile.getIdentifier())){
            this.availableLanguage.add(languageFile.getIdentifier());
            this.languageNameMapping.put(languageFile.getIdentifier(), languageFile.getName());
        }
    }

    /**
     * Mit dieser Methode kann die bevorzugte Sprache eines Spielers eingestellt werden.
     * @param uuid Die UUID des Spielers
     * @param language Der name der Sprache
     */
    public void setLanguage(UUID uuid, String language){
        String resolvedLanguage = availableLanguage.contains(language) ? language : this.mapLanguageName(language);
        this.playerLanguageHashMap.replace(uuid, resolvedLanguage);
        this.databaseConnector.updatePlayer(uuid, resolvedLanguage);
    }

    /**
     * Ueberprueft, ob der Name einer Sprache registriert wurde.
     * @param language Der Name der Sprache
     * @return Wahrheitswert, ob die Sprache schon registriert worden ist
     */
    public boolean containsLanguage(String language){
        return this.availableLanguage.contains(language) || this.languageNameMapping.containsValue(language);
    }

    /**
     * Gibt den String zurueck, der in der bevorzugten Sprache des Spielers unter dem angegebenen Key zu finden ist.
     * Wenn die bevorzugte Sprache nicht vorhanden ist, wird die Standartsprache des jeweiligen Plugins benutzt.
     * @param plugin Das Plugin, von welchem der String registriert wurde
     * @param uuid Die UUID des Spielers, dem dieser String angezeigt werden soll
     * @param messageKey Der Key, unter welchem der String in der .language Datei gespeichert ist
     * @return Den String in der gewuenschten Sprache oder alternativ den String in der Standartsprache des Plugins
     */
    public String getTranslation(Plugin plugin, UUID uuid, String messageKey){
        if (languageFiles.get(plugin).containsKey(playerLanguageHashMap.get(uuid))){
            return this.languageFiles.get(plugin).get(playerLanguageHashMap.get(uuid)).getTranslation(messageKey);
        } else {
            for (String currentLanguage:
                 languageFiles.get(plugin).keySet()) {
                String localeTop = currentLanguage.split("_")[0];
                if (playerLanguageHashMap.get(uuid).split("_")[0].equals(localeTop)){
                    return this.languageFiles.get(plugin).get(currentLanguage).getTranslation(messageKey);
                }
            }

            return this.languageFiles.get(plugin).get(defaultLanguage).getTranslation(messageKey);
        }
    }

    /**
     * Mit dieser Methode kann der Name einer Sprache anhand der Abkuerzung herausgefunden werden
     * @param languageCode Die Abkuerzung der Sprache
     * @return Der Name der Sprache
     */
    public String mapLanguageCode(String languageCode){
        return this.languageNameMapping.getOrDefault(languageCode, languageCode);
    }

    /**
     * Mit dieser Methode kann die Abkuerzung einer Sprache anhand des Namens herausgefunden werden
     * @param languageName Der Name der Sprache
     * @return Die Abkuerzung der Sprache
     */
    public String mapLanguageName(String languageName){
        for (String languageCode:
             languageNameMapping.keySet()) {
            if (languageNameMapping.get(languageCode).equals(languageName)){
                return languageCode;
            }
        }
        return languageName;
    }

    /**
     * Diese Methode gibt die Bezeichnung der Sprache zurueck, welche ein Spieler eingestellt hat.
     * @param uuid Die UUID des Spielers
     * @return Der Name der Sprache, die der Spieler eingestellt hat
     */
    public String getPlayerLanguageName(UUID uuid){
        return mapLanguageCode(playerLanguageHashMap.get(uuid));
    }

    /**
     * Legt fest, ob fuer einen Spieler die Sprache automatisch erkannt werden soll.
     * @param uuid Die UUID des Spielers
     * @param value Wahrheitswert, ob die Erkennung der Sprache automatisch ausgefuehrt werden soll
     */
    public void setPlayerAutoUpdateLanguage(UUID uuid, boolean value){
        this.playerAutoChangeLanguage.replace(uuid, value);
        databaseConnector.updatePlayerAutoDetection(uuid, value);
    }

    /**
     * Mit dieser Methode koennen Namen fuer Sprachkennungen hinzugefuegt werden
     * @param languageCode Die Abkuerzung der Sprache
     * @param languageName Der gewuenschte Name der Sprache
     */
    private void addMapping(String languageCode, String languageName){
        this.languageNameMapping.put(languageCode, languageName);
    }

    /**
     * Bei der Initialisierung werden alle Daten fuer bereits registrierte Spieler aus der Datenbank geladen.
     */
    private void init(){
        this.defaultLanguage = ConfigurationLoader.getDefaultLang();
        for (UUID uuid:
                databaseConnector.getRegisteredPlayers()) {
            this.playerLanguageHashMap.put(uuid, databaseConnector.loadPlayer(uuid));
            this.playerAutoChangeLanguage.put(uuid, databaseConnector.hasAutoUpdate(uuid));
        }
    }

    /*-----------------------------------------GETTER AND SETTER------------------------------------------------------*/

    public static LanguageManager getInstance() {
        return instance;
    }

    public List<String> getAvailableLanguage(){
        return availableLanguage;
    }

}
