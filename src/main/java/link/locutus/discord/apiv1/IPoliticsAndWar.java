package link.locutus.discord.apiv1;

import link.locutus.discord.apiv1.domains.*;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;

public interface IPoliticsAndWar {

  Nation getNation(int nationId) throws IOException;

  Nations getNations() throws IOException;

  Nations getNations(boolean vm) throws IOException;

  Nations getNationsByAlliance(boolean vm, int allianceId) throws IOException;

  Nations getNationsByScore(boolean vm, int maxScore, int minScore) throws IOException;

  Nations getNations(boolean vm, int allianceId, int maxScore, int minScore) throws IOException;

  Alliance getAlliance(int allianceId) throws IOException;

  AllianceMembers getAllianceMembers(int allianceId) throws IOException;

  Alliances getAlliances() throws IOException;

  NationMilitary getAllMilitaries() throws IOException;

  AllCities getAllCities() throws IOException;

  Applicants getApplicants(int allianceId) throws IOException;

  Bank getBank(int allianceId) throws IOException;

  Members getMembers(int allianceId) throws IOException;

  City getCity(int cityId) throws IOException;

  War getWar(int warId) throws IOException;

  Wars getWars() throws IOException;

  Wars getWarsByAmount(int amount) throws IOException;

  Wars getWarsByAlliance(Integer[] alliance_ids) throws IOException;

  Wars getWars(int amount, Integer[] alliance_ids) throws IOException;

  TradePrice getTradeprice(ResourceType resource) throws IOException;

  TradeHistory getAllTradehistory() throws IOException;

  TradeHistory getTradehistoryByType(ResourceType[] resources) throws IOException;

  TradeHistory getTradehistoryByAmount(Integer amount) throws IOException;

  TradeHistory getTradehistory(Integer amount, ResourceType[] resources) throws IOException;

  WarAttacks getWarAttacks() throws IOException;

  WarAttacks getWarAttacksByWarId(int warId) throws IOException;

  WarAttacks getWarAttacksByMinWarAttackId(int minWarAttackId) throws IOException;

  WarAttacks getWarAttacksByMaxWarAttackId(int maxWarAttackId) throws IOException;

  WarAttacks getWarAttacks(int warId, int minWarAttackId, int maxWarAttackId) throws IOException;
}
