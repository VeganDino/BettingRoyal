package com.ssafy.api.service;

import com.ssafy.db.entity.Badge;
import com.ssafy.db.repository.BadgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BadgeService {

    @Autowired
    private BadgeRepository badgeRepository;

    public void createBadge(Badge badge){
        badgeRepository.save(badge);
    }

    public void modifyBadge(Badge badge){
        badgeRepository.save(badge);
    }

    public void deleteBadge(int badgeId){
        badgeRepository.deleteByBadgeId(badgeId);
    }

    public List<Badge> searchBadges(){
        return badgeRepository.findAll();
    }

    public Badge searchBadge(Integer badgeId){
        return badgeRepository.findById(badgeId).get();
    }
}
