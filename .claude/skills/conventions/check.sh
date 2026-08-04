#!/usr/bin/env bash
# Checks the package structure and naming rules described in SKILL.md.
# Prints "path:line  rule  detail" per violation; exits 1 if any fired.
set -uo pipefail

cd "$(dirname "$0")/../../.." || exit 2
SKILL=".claude/skills/conventions/SKILL.md"
PKG="nl.jjt.vorfahrtfahrradcompanion"
PKGPATH="nl/jjt/vorfahrtfahrradcompanion"
# Violations are tallied in a file, not a variable: most checks run inside a pipeline, whose subshell
# would throw the count away and leave the script exiting 0 with violations on screen.
TALLY=$(mktemp); trap 'rm -f "$TALLY"' EXIT

say() { printf '%s\n  %s  %s\n' "$1" "$2" "$3"; echo x >>"$TALLY"; }

# All Kotlin sources, and their package path relative to the root package ("" for the root package).
sources() { find shared/src androidApp/src -name '*.kt' 2>/dev/null | sort; }
pkgdir() { local p="${1#*/kotlin/$PKGPATH}"; p="${p%/*.kt}"; p="${p#/}"; [[ "$p" == *.kt ]] && p=""; echo "$p"; }

# The manifest: the fenced block under "## Folder structure", first column of each line.
manifest=$(awk '/^## Folder structure/{f=1} f&&/^```$/{c++; if(c==2) exit} f&&c==1' "$SKILL" \
  | grep -oE '^[a-z(][a-zA-Z/()]*' | sed 's|^(root)$||' | sort -u)

for f in $(sources); do
  d=$(pkgdir "$f")
  # 1. package is in the manifest
  grep -qxF "$d" <<<"$manifest" || say "$f:1" "structure" "package '${d:-(root)}' is not in SKILL.md#folder-structure"

  # 2. package declaration matches the directory
  want="$PKG${d:+.${d//\//.}}"
  got=$(sed -n '1s/^package //p' "$f")
  [ "$got" = "$want" ] || say "$f:1" "package" "declares '$got', directory says '$want'"

  # 3. the file name names what is in it: some top-level declaration shares its name, allowing a
  #    verb prefix (create/remember/platform), an extension receiver, and an Android/Ios file prefix.
  base=$(basename "$f" .kt)
  cmp="$base"
  [[ "$f" == *"/androidMain/"* || "$f" == *"/iosMain/"* ]] && cmp="${cmp#Android}" && cmp="${cmp#Ios}"
  names=$(grep -oE '^(internal |private |public |abstract |sealed |data |value |expect |actual |enum )*(class|interface|object|fun|val) +([A-Za-z_][A-Za-z0-9_]*\.)?[A-Za-z_][A-Za-z0-9_]*' "$f" \
    | sed -E 's/.*(class|interface|object|fun|val) +//' | tr -d ' ')
  match=0
  for n in $names; do
    recv="${n%%.*}"; simple="${n##*.}"
    # a file holding a group takes the plural: Routes.kt for CriteriaRoute, Migrations.kt for MIGRATION_*
    lb=$(tr '[:upper:]' '[:lower:]' <<<"${cmp%s}"); ls=$(tr '[:upper:]' '[:lower:]' <<<"$simple")
    lr=$(tr '[:upper:]' '[:lower:]' <<<"$recv" | tr -d '_')
    lsn=$(tr -d '_' <<<"$ls")
    # declaration contains the file name, file name contains the declaration, or the file is an
    # extension file named <Receiver><WhatItAdds>
    if [[ "$lsn" == *"$lb"* || "$lb" == *"$lsn"* || ( "$n" == *.* && "$lb" == "$lr"* ) ]]; then match=1; break; fi
  done
  [ "$match" -eq 0 ] && say "$f:1" "file-name" "nothing in this file is called '$cmp'"

  # 13. commonMain stays platform-free
  if [[ "$f" == *"/commonMain/"* ]]; then
    grep -nE '^import (java|javax|android)\.' "$f" \
      | while IFS=: read -r n line; do say "$f:$n" "common-main" "${line# } is not multiplatform"; done
  fi

  # 4/5/6. layer rules
  case "$d" in
    db|db/*) ;;
    *) grep -nE '^import androidx\.room\.|^@(Entity|Dao|Database)\b' "$f" \
         | while IFS=: read -r n _; do say "$f:$n" "room-in-db" "Room belongs under db/"; done ;;
  esac

  if [[ "$d" == domain/* ]]; then
    grep -nE "^import ($PKG\.(db|service|ui|di)\.|androidx\.(room|compose|lifecycle)\.|io\.ktor\.)" "$f" \
      | while IFS=: read -r n line; do say "$f:$n" "domain-purity" "domain must not import ${line#import }"; done
  fi

  case "$d" in
    ui|ui/*|location|"") ;;
    *) grep -n '@Composable' "$f" | head -1 \
         | while IFS=: read -r n _; do say "$f:$n" "composable-in-ui" "@Composable outside ui/, location/"; done ;;
  esac

  # 7. entity/dao annotations match the file name
  [[ "$base" == *Entity && "$d" == db/* ]] && ! grep -q '@Entity' "$f" && say "$f:1" "entity" "named *Entity but carries no @Entity"
  [[ "$base" == *Dao && "$d" == db/* ]] && ! grep -q '@Dao' "$f" && say "$f:1" "dao" "named *Dao but carries no @Dao"

  # 12. Repository is not part of the vocabulary
  grep -nE '\b(class|interface|object) +[A-Za-z]*Repository\b' "$f" \
    | while IFS=: read -r n _; do say "$f:$n" "vocabulary" "Repository is not used; a DAO wrapper is a Store, an in-memory owner is a Recorder"; done

  # 16. const vals and enum entries are SCREAMING_SNAKE
  grep -nE '^\s*(private |internal |public )?const val [a-z]' "$f" \
    | while IFS=: read -r n line; do say "$f:$n" "casing" "const val ${line##*const val } should be SCREAMING_SNAKE"; done

  # 17. CompositionLocals are Local*
  grep -nE 'val [A-Za-z_]+ *(:[^=]*)?= *(staticC|c)ompositionLocalOf' "$f" | grep -vE 'val Local' \
    | while IFS=: read -r n _; do say "$f:$n" "compositional-local" "a CompositionLocal is named Local<Thing>"; done

  # 22/23. test naming
  if [[ "$f" == *"/commonTest/"* || "$f" == *"Test/"* ]]; then
    grep -nE '^\s*fun `' "$f" | while IFS=: read -r n _; do say "$f:$n" "test-name" "test methods are camelCase, not backticked"; done
    grep -nE '^\s*fun (should|test)[A-Z]' "$f" | while IFS=: read -r n _; do say "$f:$n" "test-name" "drop the should/test prefix"; done
    grep -nE '\bclass Fake[A-Za-z]+' "$f" | grep -v "/testing/" >/dev/null 2>&1 && [[ "$d" != "testing" ]] \
      && grep -nE '^\s*(private )?class Fake[A-Za-z]+' "$f" \
      | while IFS=: read -r n _; do say "$f:$n" "fakes" "fakes live in the testing package"; done
  fi
done

# 8. a Dao is used only inside its own db/<feature> package. AppDatabase declares the accessors, so
#    the db root is exempt, and so are tests, which fake the DAO to exercise the Store over it.
for dao in $(grep -rlE '^@Dao' shared/src --include='*.kt' 2>/dev/null); do
  name=$(basename "$dao" .kt); home=$(pkgdir "$dao")
  grep -rn "^import $PKG\.${home//\//.}\.$name\$" shared/src --include='*.kt' \
    | while IFS=: read -r f n _; do
        [[ "$f" == *"Test/"* ]] && continue
        d=$(pkgdir "$f")
        [ "$d" = "$home" ] || [ "$d" = "db" ] \
          || say "$f:$n" "dao-privacy" "$name is used outside ${home}; go through its Store"
      done
done

# 9. Room<X>Store implements an <X>Store declared outside db
for s in $(find shared/src -name 'Room*Store.kt' 2>/dev/null); do
  port="$(basename "$s" .kt)"; port="${port#Room}"
  grep -rqE "interface $port\b" shared/src/commonMain --include='*.kt' \
    || say "$s:1" "store-prefix" "no interface $port outside db; an unported store drops the Room prefix"
done

# 10. every entity is registered in AppDatabase
db="shared/src/commonMain/kotlin/$PKGPATH/db/AppDatabase.kt"
if [ -f "$db" ]; then
  for e in $(grep -rlE '^@Entity' shared/src --include='*.kt' 2>/dev/null); do
    n=$(basename "$e" .kt)
    grep -q "$n::class" "$db" || say "$e:1" "entity-registered" "$n is not in AppDatabase's entities"
  done
fi

# 11. table names are snake_case of the concept, or its plural
grep -rhn 'tableName = "' shared/src --include='*.kt' 2>/dev/null | sed 's/.*tableName = "\([^"]*\)".*/\1/' \
  | grep -vE '^[a-z][a-z_]*$' | while read -r t; do say "shared:0" "table-name" "'$t' is not snake_case"; done

# 14. every Android implementation of a commonMain interface has an iOS counterpart. Only interfaces:
#     AndroidAppDatabase and AndroidModule implement nothing, and iOS has no equivalent to build.
for a in $(find shared/src/androidMain -name 'Android*.kt' 2>/dev/null); do
  n=$(basename "$a" .kt); port="${n#Android}"; i="Ios$port"
  grep -rqE "^(internal )?interface $port\b" shared/src/commonMain --include='*.kt' || continue
  find shared/src/iosMain -name "$i.kt" | grep -q . \
    || say "$a:1" "ios-counterpart" "no $i.kt; the iOS stub is the whole iOS investment"
done

# 15. the exported schema directory matches AppDatabase's package
if [ -f "$db" ]; then
  want="$PKG.$(sed -n '1s/^package '"$PKG"'\.//p' "$db" 2>/dev/null || true)"
  want="${want%.}.AppDatabase"; want="${want/../.}"
  for dir in shared/schemas/*/; do
    d=$(basename "$dir")
    [ "$d" = "$PKG.db.AppDatabase" ] || say "$dir:0" "schema-dir" "expected $PKG.db.AppDatabase, found $d"
  done
fi

# 19. one *UiState per *ViewModel, named after it
for vm in $(find shared/src -name '*ViewModel.kt' 2>/dev/null); do
  n=$(basename "$vm" .kt); want="${n%ViewModel}UiState"
  grep -qE "\b(sealed interface|data class|class) $want\b" "$vm" \
    || say "$vm:1" "ui-state" "no $want declared alongside $n"
done
grep -rn 'UiState' shared/src --include='*.kt' 2>/dev/null | grep -E '(sealed interface|data class) [A-Za-z]+UiState' \
  | while IFS=: read -r f n rest; do
      t=$(sed -E 's/.*(sealed interface|data class) ([A-Za-z]+UiState).*/\2/' <<<"$rest")
      base="${t%UiState}"
      find shared/src -name "${base}ViewModel.kt" | grep -q . || say "$f:$n" "ui-state" "$t has no ${base}ViewModel"
    done

# 20. every *Screen is reachable from the routes
routes="shared/src/commonMain/kotlin/$PKGPATH/ui/navigation/Routes.kt"
if [ -f "$routes" ]; then
  for s in $(find shared/src -name '*Screen.kt' 2>/dev/null); do
    n=$(basename "$s" .kt)
    grep -rq "\b$n(" shared/src/commonMain/kotlin/$PKGPATH/App.kt 2>/dev/null \
      || grep -rq "\b$n(" shared/src --include='*Screen.kt' \
      || say "$s:1" "screen-routing" "$n is never navigated to"
  done
fi

# 18. Koin modules bind their own layer
mods="shared/src/commonMain/kotlin/$PKGPATH/di/AppModules.kt"
if [ -f "$mods" ]; then
  grep -nE '^val [a-z][A-Za-z]*Module' "$mods" >/dev/null || say "$mods:1" "di" "modules are named <layer>Module"
fi

fails=$(wc -l <"$TALLY" | tr -d ' ')
if [ "$fails" -eq 0 ]; then echo "conventions: clean"; else echo; echo "conventions: $fails violation(s)"; fi
[ "$fails" -eq 0 ]
