package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.db.entities.DBNationCache;

public interface DBNationGetter {
    int _nationId();
    String _nation();
    String _leader();
    String _discordStr();
    int _allianceId();
    long _lastActiveMs();
    double _score();
    int _cities();
    DomesticPolicy _domesticPolicy();
    WarPolicy _warPolicy();
    int _soldiers();
    int _tanks();
    int _aircraft();
    int _ships();
    int _missiles();
    int _nukes();
    int _spies();
    long _enteredVm();
    long _leavingVm();
    NationColor _color();
    long _date();
    Rank _rank();
    int _alliancePosition();
    Continent _continent();
    long _projects();
    long _cityTimer();
    long _projectTimer();
    long _beigeTimer();
    long _warPolicyTimer();
    long _domesticPolicyTimer();
    long _colorTimer();
    long _espionageFull();
    int _dcTurn();
    int _warsWon();
    int _warsLost();
    int _taxId();
    double _gni();
    double _costReduction();
    int _researchBits();
    DBNationCache _cache();
}
