# Projet L3: Chat Fédéré

## Description
Application de messagerie instantanée nommée "ChatAmu" permettant de discuter à travers des salons pouvant être créés par les utilisateurs. 

------------
### Exécution de l'application
1. Compiler les fichiers dans le dossier 'servers' avec la commande: javac *.java

2. Exécuter un serveur dans un terminal en tapant la commande: java ChatamuCentral <port>

3. Exécuter l'interface client avec la méthode 'run' de Gradle

4. Saisir le port du serveur lancé dans le champ pour s'y connecter

5. Choisir un identifiant pour se connecter

6. Pour envoyer un message taper le message dans le champ et appuyer sur entrée

7. Pour changer de salon, créer un salon et cliquer sur le salon à gauche

(OPTIONEL)

8. Pour connecter deux serveurs entre eux, exécuter un second serveur ChatamuCentral sur un autre port

9. Dans le terminal, taper la commande 'SERVERCONNECT <port>' avec comme port passé en paramètre le port du premier serveur exécuté

10. Les deux serveurs sont maintenant liés, lorsque qu'un serveur tombe en panne, les clients seront réaffectés automatiquement sur les serveurs en fonctionnement

------------
### Technologies principales
- [x] JAVA
- [x] JAVAFX
