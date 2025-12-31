Sprint6_ter - Framework core
===========================

Ce module contient uniquement le code source du micro-framework (annotations, route, scanner, servlet, ModelView).

Usage rapide:
- Compiler avec Maven (option `-parameters` activée dans le `pom.xml`).
- Déployer le WAR dans un conteneur Jakarta Servlet (Tomcat/Jetty).
- Annoter vos contrôleurs applicatifs dans un projet séparé avec `@Controller` et `@URLMapping("/path/{id}")`.

Exigences:
- Le module ne contient pas de contrôleurs d'exemple.
- Pour que le binding par nom fonctionne, compilez les contrôleurs avec `-parameters`.
