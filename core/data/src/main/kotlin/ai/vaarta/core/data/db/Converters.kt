package ai.vaarta.core.data.db

import androidx.room.TypeConverter

/** Stores the small enums as their stable names — human-readable in the DB and refactor-safe by value. */
class Converters {
    @TypeConverter fun sourceToString(v: SessionSource): String = v.name
    @TypeConverter fun sourceFromString(v: String): SessionSource =
        runCatching { SessionSource.valueOf(v) }.getOrDefault(SessionSource.LIVE)

    @TypeConverter fun kindToString(v: TurnKind): String = v.name
    @TypeConverter fun kindFromString(v: String): TurnKind =
        runCatching { TurnKind.valueOf(v) }.getOrDefault(TurnKind.CALLER)
}
